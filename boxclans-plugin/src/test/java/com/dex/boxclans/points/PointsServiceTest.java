package com.dex.boxclans.points;

import com.dex.boxclans.api.PointResult;
import com.dex.boxclans.api.PointResult.Status;
import com.dex.boxclans.api.event.ClanPointsAwardedEvent;
import com.dex.boxclans.api.event.ClanPointsGainEvent;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.TestEnv;
import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.storage.Database;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointsServiceTest {

    private static final Logger LOG = Logger.getLogger("test");
    private static final long DAY = 86_400_000L;

    private static final String CONFIG = """
            allow-negative: %s
            boxcore-boost-type: 'clan'
            day-reset-timezone: 'Asia/Ho_Chi_Minh'
            sources:
              cd:     { points: 10, cooldown: 60, display: 'Cooldown' }
              pcap:   { points: 5, daily-cap: 12 }
              ccap:   { points: 10, clan-daily-cap: 25, display: 'Clan cap' }
              every3: { points: 1, every: 3 }
              loss:   { points: -50 }
              closed: { points: 10, enabled: false }
              boosted: { points: 10, boost: true }
              team:   { points: 7, contribution: false }
              quest:  { points: 0 }
              kill:   { points: 10, clan-daily-cap: 500, display: 'Hạ gục người chơi' }
            kill-events:
              loan-chien: { display: 'LOẠN CHIẾN', kill: 10, death: -5, ignore-limits: true }
            """;

    record Gained(UUID clan, UUID player, String display, long amount) {}

    @TempDir
    File dir;
    TestEnv env;
    PointsService points;
    final List<Gained> gained = new ArrayList<>();
    final List<String> capsReached = new ArrayList<>();
    Consumer<Event> hook;
    UUID leader;
    UUID member;
    ClanData clan;

    static PointsConfig config(boolean allowNegative) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(CONFIG.formatted(allowNegative));
        InputStream bundled = PointsServiceTest.class.getClassLoader().getResourceAsStream("points.yml");
        y.setDefaults(YamlFiles.readBundled(bundled, "points.yml", LOG));
        return PointsConfig.from(y, LOG);
    }

    PointsService service(PointsConfig cfg) {
        return new PointsService(env.manager, env.db, env.eventSink(e -> {
            if (hook != null) hook.accept(e);
        }), env.clock::get, cfg, new PointsService.Announcer() {
            @Override
            public void gained(ClanData c, UUID player, String display, long amount, long total) {
                gained.add(new Gained(c.uuid(), player, display, amount));
            }

            @Override
            public void clanCapReached(ClanData c, PointSource source, long cap) {
                capsReached.add(c.key() + ":" + source.id() + ":" + cap);
            }
        });
    }

    @BeforeEach
    void setUp() throws Exception {
        env = new TestEnv(dir, 3);
        // 2026-10-06 03:00 UTC = 10:00 in Ho Chi Minh
        env.clock.set(1_791_255_600_000L);
        points = service(config(false));
        leader = env.player();
        member = env.player();
        clan = env.clanWith(leader, "Alpha", List.of(member));
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private PointResult award(UUID p, String source) {
        return points.award(p, source, null, "test", false);
    }

    @Test
    void cooldownPerPlayer() {
        assertEquals(Status.SUCCESS, award(leader, "cd").status());
        assertEquals(Status.COOLDOWN, award(leader, "cd").status());
        assertEquals(Status.SUCCESS, award(member, "cd").status(), "cooldown is per player");
        env.clock.addAndGet(59_000);
        assertEquals(Status.COOLDOWN, award(leader, "cd").status());
        env.clock.addAndGet(1_000);
        assertEquals(Status.SUCCESS, award(leader, "cd").status());
        assertEquals(30, clan.points());
    }

    @Test
    void cleanupDropsOnlyFinishedCooldowns() {
        award(leader, "cd");
        env.clock.addAndGet(30_000);
        award(member, "cd");
        assertEquals(2, points.cooldownEntries());

        // 60 s after the leader's award: only the leader's cooldown is over
        env.clock.addAndGet(30_000);
        points.cleanup();
        assertEquals(1, points.cooldownEntries());
        assertEquals(Status.COOLDOWN, award(member, "cd").status(), "a running cooldown survives the cleanup");

        env.clock.addAndGet(30_000);
        points.cleanup();
        assertEquals(0, points.cooldownEntries());
    }

    @Test
    void forgetDropsTheEveryCountOfAPlayerWhoLeft() {
        award(leader, "every3");
        award(leader, "every3");
        award(member, "every3");
        points.forget(leader);
        assertEquals(0, points.accumulator().pending(DailyCounters.player(leader) + "|every3"));
        assertEquals(1, points.accumulator().pending(DailyCounters.player(member) + "|every3"), "others keep theirs");
        assertEquals(Status.ACCUMULATED, award(leader, "every3").status(), "the leader starts again at 0");
    }

    @Test
    void playerDailyCapCutsToFitAndResetsAtMidnight() {
        assertEquals(5, award(leader, "pcap").amount());
        assertEquals(5, award(leader, "pcap").amount());
        PointResult cut = award(leader, "pcap");
        assertEquals(Status.SUCCESS, cut.status());
        assertEquals(2, cut.amount(), "12 per day: only 2 left");
        assertEquals(Status.DAILY_CAP, award(leader, "pcap").status());
        assertEquals(5, award(member, "pcap").amount(), "the cap is per player");

        // 10:00 + 13 h = 23:00 same day, then 01:00 next day (Asia/Ho_Chi_Minh)
        env.clock.addAndGet(13 * 3_600_000L);
        assertEquals(Status.DAILY_CAP, award(leader, "pcap").status());
        env.clock.addAndGet(2 * 3_600_000L);
        assertEquals(5, award(leader, "pcap").amount(), "new day at 0h");
    }

    @Test
    void clanDailyCapIsSharedCutToFitAndAnnouncedOnce() {
        assertEquals(10, award(leader, "ccap").amount());
        assertEquals(10, award(member, "ccap").amount());
        PointResult cut = award(leader, "ccap");
        assertEquals(5, cut.amount(), "25 per clan: 5 left");
        assertEquals(List.of("Alpha:ccap:25"), capsReached, "announced when the cap is reached");
        assertEquals(Status.CLAN_DAILY_CAP, award(member, "ccap").status());
        assertEquals(Status.CLAN_DAILY_CAP, award(leader, "ccap").status());
        assertEquals(1, capsReached.size(), "only once per day");
        assertEquals(25, points.clanToday(clan.uuid(), "ccap"));

        UUID other = env.player();
        ClanData beta = env.clanWith(other, "Beta", List.of());
        assertEquals(10, award(other, "ccap").amount(), "another clan has its own cap");
        assertEquals(10, beta.points());

        env.clock.addAndGet(DAY);
        assertEquals(10, award(leader, "ccap").amount());
    }

    @Test
    void everyAccumulates() {
        assertEquals(Status.ACCUMULATED, award(leader, "every3").status());
        assertEquals(Status.ACCUMULATED, award(leader, "every3").status());
        PointResult third = award(leader, "every3");
        assertEquals(Status.SUCCESS, third.status());
        assertEquals(1, third.amount());
        assertEquals(Status.ACCUMULATED, award(member, "every3").status(), "counted per player");
        assertEquals(Status.ACCUMULATED, award(leader, "every3").status(), "count restarts");
    }

    @Test
    void allowNegativeFalseClampsAtZero() {
        assertEquals(Status.ZERO, award(leader, "loss").status());
        clan.points(30);
        PointResult r = award(leader, "loss");
        assertEquals(-30, r.amount());
        assertEquals(0, clan.points());
        PointResult take = points.adjust(clan, -100, "admin", "staff", true);
        assertEquals(Status.ZERO, take.status());
    }

    @Test
    void allowNegativeTrue() throws Exception {
        points = service(config(true));
        assertEquals(-50, award(leader, "loss").amount());
        assertEquals(-50, clan.points());
        assertEquals(-50, clan.member(leader).contribution());
    }

    @Test
    void sourcesAndClans() {
        assertEquals(Status.UNKNOWN_SOURCE, award(leader, "nope").status());
        assertEquals(Status.SOURCE_DISABLED, award(leader, "closed").status());
        assertEquals(Status.NO_CLAN, award(UUID.randomUUID(), "cd").status());
        assertEquals(Status.NO_CLAN, points.awardClan(UUID.randomUUID(), "cd", null, "t", false).status());
        assertEquals(Status.ZERO, award(leader, "quest").status());
        assertEquals(40, points.award(leader, "quest", 40L, "t", true).amount(), "amount overrides");
        assertEquals(40, clan.member(leader).contribution());
        assertTrue(gained.isEmpty(), "silent");

        assertEquals(7, award(member, "team").amount());
        assertEquals(0, clan.member(member).contribution(), "contribution: false");

        PointResult koth = points.awardClan(clan.uuid(), "cd", 50L, "api", false);
        assertEquals(50, koth.amount());
        assertEquals(97, clan.points());
        assertEquals(97, env.index.get(clan.uuid()).points(), "readers see the new points");
        Gained last = gained.get(gained.size() - 1);
        assertEquals(null, last.player());
        assertEquals("Cooldown", last.display());
    }

    @Test
    void gainEventCanCancelOrChangeAndBoostApplies() {
        hook = e -> {
            if (e instanceof ClanPointsGainEvent g) {
                if (g.source().equals("cd")) g.setCancelled(true);
                if (g.source().equals("pcap")) g.setAmount(g.amount() * 2);
            }
        };
        assertEquals(Status.CANCELLED, award(leader, "cd").status());
        assertEquals(10, award(leader, "pcap").amount(), "x2 by a listener");
        assertEquals(2, award(leader, "pcap").amount(), "then cut to the daily cap (12)");

        points.booster((p, type) -> type.equals("clan") ? 1.5 : 1.0);
        assertEquals(15, award(leader, "boosted").amount());
        assertEquals(10, award(leader, "ccap").amount(), "boost only for boost: true");
        List<ClanPointsAwardedEvent> awarded = env.eventsOf(ClanPointsAwardedEvent.class);
        assertEquals(15, awarded.get(awarded.size() - 2).amount());
    }

    @Test
    void killEventPointsAreNotCounted() {
        PointsConfig.KillEventProfile profile = points.config().killEvent("loan-chien");
        assertNotNull(profile);
        clan.points(100);
        for (int i = 0; i < 60; i++) points.awardKillEvent(leader, profile, true);
        assertEquals(700, clan.points(), "no cap during the event");
        assertEquals(0, points.clanToday(clan.uuid(), "kill"), "the daily counter does not move");
        assertEquals(-5, points.awardKillEvent(member, profile, false).amount(), "death costs points");
        assertEquals(695, clan.points());
    }

    @Test
    void countersAndLogSurviveARestart() throws Exception {
        award(leader, "ccap");
        award(leader, "ccap");
        points.adjust(clan, 100, "clan-war-win", "staff", false);
        points.set(clan, 5, "set", "staff");
        env.reload();
        points = service(config(false));
        points.daily().load();
        clan = env.manager.clan(clan.uuid());
        assertEquals(5, clan.points());
        assertEquals(20, points.clanToday(clan.uuid(), "ccap"));
        assertEquals(5, award(leader, "ccap").amount(), "the cap remembers today's 20 points");
        Database.LogPage page = env.db.logPage(clan.uuid(), 0, 10).get();
        assertEquals(5, page.total());
        assertEquals("ccap", page.rows().get(0).source());
        assertEquals(5, page.rows().get(0).delta());
        assertEquals("set", page.rows().get(1).source());
        assertEquals(-115, page.rows().get(1).delta());
        assertEquals("staff", page.rows().get(1).by());
    }
}
