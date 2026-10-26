package com.dex.boxclans.clan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** "Online lần cuối": set on create / accept / join / quit, stored in {@code members.last_seen}, back-filled once. */
class LastSeenTest {

    @TempDir
    File dir;
    TestEnv env;
    UUID leader;
    UUID member;
    ClanData clan;

    @BeforeEach
    void setUp() {
        env = new TestEnv(dir, 1);
        leader = env.player();
        member = env.player();
        clan = env.clanWith(leader, "Alpha", List.of(member));
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private long snapshotLastSeen(UUID player) {
        return env.manager.snapshot(clan).memberInfo().get(player).lastSeen();
    }

    @Test
    void createAndAcceptCountAsSeen() {
        assertEquals(env.clock.get(), clan.member(leader).lastSeen());
        assertEquals(env.clock.get(), clan.member(member).lastSeen());
    }

    @Test
    void seenIsPublishedAndSurvivesARestart() throws Exception {
        long start = env.clock.get();
        env.clock.addAndGet(5_000);
        env.manager.seen(member);
        assertEquals(start + 5_000, snapshotLastSeen(member), "the menu reads the published snapshot");
        assertEquals(start, snapshotLastSeen(leader));

        env.reload();
        ClanData again = env.manager.clan(clan.uuid());
        assertEquals(start + 5_000, again.member(member).lastSeen());
        assertEquals(start, again.member(leader).lastSeen());
    }

    @Test
    void joinUpdatesLastSeen() {
        env.clock.addAndGet(60_000);
        env.manager.onJoin(leader);
        assertEquals(env.clock.get(), snapshotLastSeen(leader));
    }

    @Test
    void backfillOnlyFillsUnknownMembers() throws Exception {
        long known = clan.member(leader).lastSeen();
        clan.member(member).lastSeen(0);
        int filled = env.manager.backfillLastSeen(id -> id.equals(member) ? 42L : 999L);
        assertEquals(1, filled);
        assertEquals(42L, snapshotLastSeen(member));
        assertEquals(known, snapshotLastSeen(leader), "a known value is never replaced");
        assertEquals(0, env.manager.backfillLastSeen(id -> 7L), "nothing left to fill");

        env.reload();
        assertEquals(42L, env.manager.clan(clan.uuid()).member(member).lastSeen());
    }

    @Test
    void unknownStaysZeroAndClanlessPlayersAreIgnored() {
        clan.member(member).lastSeen(0);
        assertEquals(0, env.manager.backfillLastSeen(id -> 0L));
        assertEquals(0, clan.member(member).lastSeen(), "the server knows nothing either: stays unknown");
        env.manager.seen(env.player()); // not in a clan: nothing happens
    }
}
