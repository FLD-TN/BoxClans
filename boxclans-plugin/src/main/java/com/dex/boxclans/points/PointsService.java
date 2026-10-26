package com.dex.boxclans.points;

import com.dex.boxclans.api.PointResult;
import com.dex.boxclans.api.PointResult.Status;
import com.dex.boxclans.api.event.ClanPointsAwardedEvent;
import com.dex.boxclans.api.event.ClanPointsGainEvent;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanManager;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.storage.Database;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Clan points (§5.1). <b>Main thread only.</b> Order of one award: source enabled → cooldown → {@code every} →
 * caps reached? → BoxCore boost → {@link ClanPointsGainEvent} → cut to the caps → {@code allow-negative} →
 * clan points and contribution → {@code points_log}, daily counters, announce → {@link ClanPointsAwardedEvent}.
 */
public final class PointsService {

    /** Messages for the clan (the plugin sends them; tests record them). */
    public interface Announcer {
        /** Points were added; not called for silent awards or sources with {@code announce: false}. */
        void gained(ClanData clan, UUID player, String display, long amount, long total);

        /** The clan reached a source's {@code clan-daily-cap} today (called once per clan, source and day). */
        void clanCapReached(ClanData clan, PointSource source, long cap);
    }

    /** BoxCore boost multiplier (§10.4); 1 when BoxCore is absent. */
    @FunctionalInterface
    public interface Booster {
        double multiplier(UUID player, String boostType);
    }

    /** How one award is handled. */
    record Rules(String by, boolean silent, boolean playerRules, boolean checkClanCap, boolean countDaily,
                 boolean fireGain, String logSource) {}

    public static final String BY_SYSTEM = "system";

    private final ClanManager manager;
    private final Database db;
    private final ClanManager.Events events;
    private final LongSupplier clock;
    private final DailyCounters daily;
    private final PointAccumulator accumulator = new PointAccumulator();
    private final Map<String, Long> lastAward = new HashMap<>();
    private volatile PointsConfig config;
    private volatile Announcer announcer;
    private volatile Booster booster = (p, t) -> 1.0;

    public PointsService(ClanManager manager, Database db, ClanManager.Events events, LongSupplier clock,
                         PointsConfig config, Announcer announcer) {
        this.manager = manager;
        this.db = db;
        this.events = events;
        this.clock = clock;
        this.config = config;
        this.announcer = announcer;
        this.daily = new DailyCounters(db, clock, config.zone());
    }

    public void configure(PointsConfig config) {
        this.config = config;
        daily.zone(config.zone());
    }

    public void booster(Booster booster) {
        this.booster = booster == null ? (p, t) -> 1.0 : booster;
    }

    public PointsConfig config() {
        return config;
    }

    public DailyCounters daily() {
        return daily;
    }

    public PointAccumulator accumulator() {
        return accumulator;
    }

    // ------------------------------------------------------------------ awards with the source's rules

    /** Awards a source to the player's clan (contribution counted); {@code amount} overrides the source's points. */
    public PointResult award(UUID player, String sourceId, Long amount, String by, boolean silent) {
        PointSource src = config.source(sourceId);
        if (src == null) return PointResult.UNKNOWN_SOURCE;
        if (!src.enabled()) return result(Status.SOURCE_DISABLED, null);
        ClanData clan = manager.of(player);
        if (clan == null) return PointResult.NO_CLAN;
        return apply(clan, player, src, amount == null ? src.points() : amount,
                new Rules(by, silent, true, true, true, true, src.id()));
    }

    /** Awards a source to a whole clan (no contribution, no player cooldown / cap). */
    public PointResult awardClan(UUID clanUuid, String sourceId, Long amount, String by, boolean silent) {
        PointSource src = config.source(sourceId);
        if (src == null) return PointResult.UNKNOWN_SOURCE;
        if (!src.enabled()) return result(Status.SOURCE_DISABLED, null);
        ClanData clan = manager.clan(clanUuid);
        if (clan == null) return PointResult.NO_CLAN;
        return apply(clan, null, src, amount == null ? src.points() : amount,
                new Rules(by, silent, false, true, true, true, src.id()));
    }

    /**
     * A kill or death during a kill event (§5.4): profile points, logged as {@code kill-event:<profile>}. With
     * {@code ignore-clan-daily-cap} (the usual case) the daily counter is neither checked nor moved; a profile that
     * keeps the cap counts its kills like normal kills.
     */
    public PointResult awardKillEvent(UUID player, PointsConfig.KillEventProfile profile, boolean kill) {
        ClanData clan = manager.of(player);
        if (clan == null) return PointResult.NO_CLAN;
        PointSource base = config.source("kill");
        PointSource src = new PointSource("kill", kill ? profile.kill() : profile.death(), true,
                base == null ? "kill" : base.display(), 1, 0, 0,
                profile.ignoreClanDailyCap() || !kill || base == null ? 0 : base.clanDailyCap(),
                false, true, true, null);
        return apply(clan, player, src, src.points(),
                new Rules(BY_SYSTEM, false, false, kill && !profile.ignoreClanDailyCap(),
                        kill && !profile.ignoreClanDailyCap(), true, "kill-event:" + profile.id()));
    }

    private PointResult apply(ClanData clan, UUID player, PointSource src, long base, Rules rules) {
        long now = clock.getAsLong();
        String cooldownKey = player + "|" + src.id();
        if (rules.playerRules() && src.cooldownSeconds() > 0) {
            Long last = lastAward.get(cooldownKey);
            if (last != null && now - last < src.cooldownSeconds() * 1000L) return result(Status.COOLDOWN, clan);
        }
        if (src.every() > 1) {
            String key = (player != null ? DailyCounters.player(player) : DailyCounters.clan(clan.uuid())) + "|" + src.id();
            if (!accumulator.count(key, src.every())) return result(Status.ACCUMULATED, clan);
        }
        if (base == 0) return result(Status.ZERO, clan);

        String playerOwner = player == null ? null : DailyCounters.player(player);
        String clanOwner = DailyCounters.clan(clan.uuid());
        long playerCap = rules.playerRules() && player != null ? src.dailyCap() : 0;
        long clanCap = rules.checkClanCap() ? src.clanDailyCap() : 0;
        long playerUsed = playerCap > 0 ? daily.get(playerOwner, src.id()) : 0;
        long clanUsed = clanCap > 0 ? daily.get(clanOwner, src.id()) : 0;
        if (base > 0 && playerCap > 0 && playerUsed >= playerCap) return result(Status.DAILY_CAP, clan);
        if (base > 0 && clanCap > 0 && clanUsed >= clanCap) {
            if (daily.firstAnnounce(clanOwner, src.id()) && announcer != null) announcer.clanCapReached(clan, src, clanCap);
            return result(Status.CLAN_DAILY_CAP, clan);
        }

        long amount = base;
        String boostType = config.boostType();
        if (src.boost() && amount > 0 && boostType != null && !boostType.isEmpty()) {
            double m = player == null ? 1.0 : booster.multiplier(player, boostType);
            if (m > 0 && m != 1.0) amount = Math.round(amount * m);
        }
        if (rules.fireGain()) {
            ClanPointsGainEvent gain = events.call(new ClanPointsGainEvent(manager.snapshot(clan), player, src.id(), amount));
            if (gain.isCancelled()) return result(Status.CANCELLED, clan);
            amount = gain.amount();
        }
        if (amount > 0) {
            if (playerCap > 0) amount = Math.min(amount, playerCap - playerUsed);
            if (clanCap > 0) amount = Math.min(amount, clanCap - clanUsed);
        }
        if (!config.allowNegative() && clan.points() + amount < 0) amount = -clan.points();
        if (amount == 0) return result(Status.ZERO, clan);

        ClanSnapshot snap = commit(clan, src.contribution() ? player : null, amount);
        if (rules.playerRules() && src.cooldownSeconds() > 0) lastAward.put(cooldownKey, now);
        if (amount > 0 && rules.countDaily()) {
            if (player != null) daily.add(playerOwner, src.id(), amount);
            daily.add(clanOwner, src.id(), amount);
        }
        db.log(now, clan.uuid(), player, rules.logSource(), amount, rules.by());
        if (!rules.silent() && src.announce() && announcer != null) {
            announcer.gained(clan, player, src.display(), amount, clan.points());
        }
        events.call(new ClanPointsAwardedEvent(snap, player, src.id(), amount, clan.points()));
        if (clanCap > 0 && rules.countDaily() && clanUsed + amount >= clanCap
                && daily.firstAnnounce(clanOwner, src.id()) && announcer != null) {
            announcer.clanCapReached(clan, src, clanCap);
        }
        return new PointResult(Status.SUCCESS, amount, clan.points());
    }

    private static PointResult result(Status status, ClanData clan) {
        return new PointResult(status, 0, clan == null ? 0 : clan.points());
    }

    /** Applies a delta to the clan and (optionally) a member's contribution, then publishes and saves. */
    private ClanSnapshot commit(ClanData clan, UUID contributor, long amount) {
        clan.points(clan.points() + amount);
        db.savePoints(clan.uuid(), clan.points());
        if (contributor != null) {
            ClanData.Member m = clan.member(contributor);
            if (m != null) {
                m.contribution(m.contribution() + amount);
                db.saveMember(clan.uuid(), m);
            }
        }
        return manager.publish(clan);
    }

    // ------------------------------------------------------------------ admin changes (no rules, §5.5)

    /** {@code points add / take}: straight to the clan, no cooldown, boost or event; logged. */
    public PointResult adjust(ClanData clan, long delta, String source, String by, boolean silent) {
        long amount = delta;
        if (!config.allowNegative() && clan.points() + amount < 0) amount = -clan.points();
        if (amount == 0) return result(Status.ZERO, clan);
        ClanSnapshot snap = commit(clan, null, amount);
        db.log(clock.getAsLong(), clan.uuid(), null, source, amount, by);
        PointSource src = config.source(source);
        if (!silent && announcer != null) {
            announcer.gained(clan, null, src == null ? source : src.display(), amount, clan.points());
        }
        events.call(new ClanPointsAwardedEvent(snap, null, source, amount, clan.points()));
        return new PointResult(Status.SUCCESS, amount, clan.points());
    }

    /** {@code points set} / {@code reset}: sets the value; the change is logged with {@code source}. */
    public PointResult set(ClanData clan, long value, String source, String by) {
        long target = !config.allowNegative() && value < 0 ? 0 : value;
        long delta = target - clan.points();
        if (delta != 0) {
            ClanSnapshot snap = commit(clan, null, delta);
            db.log(clock.getAsLong(), clan.uuid(), null, source, delta, by);
            events.call(new ClanPointsAwardedEvent(snap, null, source, delta, clan.points()));
        }
        return new PointResult(Status.SUCCESS, delta, clan.points());
    }

    // ------------------------------------------------------------------ RAM cleanup

    /**
     * Drops the cooldowns that have run out (called every few minutes). A cooldown is never dropped while it still
     * runs, so leaving and rejoining does not skip it.
     */
    public void cleanup() {
        long now = clock.getAsLong();
        PointsConfig cfg = config;
        lastAward.entrySet().removeIf(e -> {
            String key = e.getKey();
            PointSource src = cfg.source(key.substring(key.indexOf('|') + 1));
            return src == null || now - e.getValue() >= src.cooldownSeconds() * 1000L;
        });
    }

    /** A player left the server: their unfinished {@code every: N} counts are dropped (they start again at 0). */
    public void forget(UUID player) {
        accumulator.forget(DailyCounters.player(player));
    }

    /** Cooldown entries held in RAM (tests, diagnostics). */
    int cooldownEntries() {
        return lastAward.size();
    }

    // ------------------------------------------------------------------ reads

    /** Points the clan earned today from a source (for the kill cap: §5.3). */
    public long clanToday(UUID clan, String source) {
        return daily.get(DailyCounters.clan(clan), source.toLowerCase(Locale.ROOT));
    }
}
