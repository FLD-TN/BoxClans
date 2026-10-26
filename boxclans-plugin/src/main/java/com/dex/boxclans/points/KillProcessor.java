package com.dex.boxclans.points;

import com.dex.boxclans.api.PointResult;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * One player killing another (§5.2, §5.4, §7). Main thread. Normal mode: the killer's clan gets the {@code kill}
 * source (clan daily cap included), the victim's clan the {@code death} source (0 by default). During a kill
 * event: the profile's kill / death points. Same clan: nothing. A rule A / B / C hit: nothing for anyone.
 * The rules only guard clan points earned by killing, so they apply only when the killer has a clan: a clanless
 * killer is never checked, warned, logged to {@code antialt.log} or reported to staff.
 */
public final class KillProcessor {

    /** Messages and logs; the plugin sends them, tests record them. */
    public interface Feedback {
        /** The killer gets no points because of a rule (only when the killer has a clan). */
        void blocked(UUID killer, UUID victim, KillRules.Verdict verdict);

        /** The killer's clan reached the kill cap today (§5.3). */
        void clanCap(UUID killer, long cap);

        /** Staff: likely alt account (rule A / B), at most once per pair per network window. */
        void altSuspect(UUID killer, String killerIp, UUID victim, String victimIp, KillRules.Verdict verdict);

        /** Every blocked kill goes to {@code antialt.log}. */
        void log(UUID killer, String killerIp, UUID victim, String victimIp, KillRules.Verdict verdict);
    }

    /** One kill as seen by the listener. */
    public record Kill(UUID killer, String killerIp, boolean killerBypass, UUID victim, String victimIp,
                       boolean victimBypass) {}

    /** What happened (tests, diagnostics). */
    public record Outcome(Kind kind, KillRules.Verdict verdict, PointResult killer, PointResult victim) {
        public enum Kind { NONE, SAME_CLAN, BLOCKED, DONE }
    }

    public static final String KILL = "kill";
    public static final String DEATH = "death";

    private final ClanManager manager;
    private final PointsService points;
    private final KillRules rules;
    private final KillEventManager killEvents;
    private final Feedback feedback;
    private final LongSupplier clock;
    private final Map<String, Long> staffNotified = new HashMap<>();
    private volatile long staffWindowMillis = 600_000L;

    public KillProcessor(ClanManager manager, PointsService points, KillRules rules, KillEventManager killEvents,
                         Feedback feedback, LongSupplier clock) {
        this.manager = manager;
        this.points = points;
        this.rules = rules;
        this.killEvents = killEvents;
        this.feedback = feedback;
        this.clock = clock;
    }

    /** Staff alerts are grouped per pair for this long (the rule B window, 10 minutes by default). */
    public void staffWindow(long seconds) {
        this.staffWindowMillis = Math.max(1, seconds) * 1000L;
    }

    public KillRules rules() {
        return rules;
    }

    public Outcome handle(Kill k) {
        if (k.killer() == null || k.victim() == null || k.killer().equals(k.victim())) {
            return new Outcome(Outcome.Kind.NONE, null, null, null);
        }
        ClanData killerClan = manager.of(k.killer());
        ClanData victimClan = manager.of(k.victim());
        if (killerClan == null && victimClan == null) return new Outcome(Outcome.Kind.NONE, null, null, null);
        if (killerClan == victimClan) return new Outcome(Outcome.Kind.SAME_CLAN, null, null, null);

        long now = clock.getAsLong();
        KillEventManager.Active event = killEvents == null ? null : killEvents.current();
        KillRules.Verdict verdict = KillRules.Verdict.ALLOW;
        if (killerClan != null) {
            boolean ignorePair = event != null && event.profile().ignorePairLimit();
            verdict = rules.check(k.killer(), k.killerIp(), k.victim(), k.victimIp(), now,
                    k.killerBypass() || k.victimBypass(), ignorePair);
        }
        if (verdict != KillRules.Verdict.ALLOW) {
            feedback.blocked(k.killer(), k.victim(), verdict);
            if (verdict.altSign()) {
                String pair = k.killer() + "|" + k.victim();
                Long last = staffNotified.get(pair);
                if (last == null || now - last >= staffWindowMillis) {
                    staffNotified.put(pair, now);
                    feedback.altSuspect(k.killer(), k.killerIp(), k.victim(), k.victimIp(), verdict);
                }
            }
            feedback.log(k.killer(), k.killerIp(), k.victim(), k.victimIp(), verdict);
            return new Outcome(Outcome.Kind.BLOCKED, verdict, null, null);
        }

        PointResult killerResult = null;
        if (killerClan != null) {
            killerResult = event != null
                    ? points.awardKillEvent(k.killer(), event.profile(), true)
                    : points.award(k.killer(), KILL, null, PointsService.BY_SYSTEM, false);
            if (killerResult.success()) rules.credited(k.killer(), k.victim(), k.victimIp(), now);
            if (killerResult.status() == PointResult.Status.CLAN_DAILY_CAP) {
                PointSource kill = points.config().source(KILL);
                feedback.clanCap(k.killer(), kill == null ? 0 : kill.clanDailyCap());
            }
        }
        PointResult victimResult = null;
        if (victimClan != null) {
            victimResult = event != null
                    ? points.awardKillEvent(k.victim(), event.profile(), false)
                    : points.award(k.victim(), DEATH, null, PointsService.BY_SYSTEM, false);
        }
        return new Outcome(Outcome.Kind.DONE, verdict, killerResult, victimResult);
    }

    /** Drops expired anti-farm state. */
    public void cleanup() {
        long now = clock.getAsLong();
        rules.cleanup(now);
        staffNotified.values().removeIf(t -> now - t >= staffWindowMillis);
    }
}
