package com.dex.boxclans;

import com.dex.boxclans.FakeServer.FakePlayer;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.history.HistoryType;
import com.dex.boxclans.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.2.0 (BOXCLANS_SHOP_SPEC.md §6.1): what the clan history records, its filters, retention and migration. */
class HistoryTest {

    @TempDir
    File dir;
    FakeServer s;
    FakePlayer alice;
    FakePlayer bob;
    FakePlayer carl;
    ClanData alpha;

    @BeforeEach
    void setUp() {
        s = new FakeServer(dir);
        alice = s.join("Alice");
        bob = s.join("Bob");
        carl = s.join("Carl");
        s.clan(alice, "create");
        s.type(alice, "Alpha");
        alice.click('C');
        alpha = s.manager.of(alice.id);
    }

    @AfterEach
    void tearDown() {
        s.close();
    }

    private List<Database.HistoryRow> all() throws Exception {
        return s.db.historyPage(alpha.uuid(), List.of(), 0, 100).get(5, TimeUnit.SECONDS).rows();
    }

    private Database.HistoryRow latest(HistoryType type) throws Exception {
        return all().stream().filter(r -> r.type().equals(type.key())).findFirst().orElse(null);
    }

    private void join(FakePlayer p) {
        s.clan(alice, "invite", p.name);
        s.clan(p, "accept");
    }

    @Test
    void membersAndClanChangesAreRecordedWithNames() throws Exception {
        assertEquals("Alice", latest(HistoryType.CREATE).actorName());
        join(bob);
        join(carl);
        assertEquals(List.of("Carl", "Bob"), all().stream().filter(r -> r.type().equals("JOIN"))
                .map(Database.HistoryRow::actorName).toList(), "newest first");

        s.clan(alice, "promote", "Bob");
        Database.HistoryRow role = latest(HistoryType.ROLE);
        assertEquals("Alice", role.actorName());
        assertEquals("Bob", role.targetName());
        assertEquals("ThanhVien>PhoBang", role.detail());

        s.clan(alice, "kick", "Carl");
        Database.HistoryRow kick = latest(HistoryType.KICK);
        assertEquals("Alice", kick.actorName());
        assertEquals("Carl", kick.targetName());

        s.clan(bob, "pvp");
        assertEquals(1, latest(HistoryType.PVP).amount());
        assertEquals("Bob", latest(HistoryType.PVP).actorName());

        s.clan(alice, "rename");
        s.type(alice, "&cAlpha");
        Database.HistoryRow rename = latest(HistoryType.RENAME);
        assertEquals("Alpha" + HistoryType.SEPARATOR + "&cAlpha", rename.detail());

        s.clan(bob, "leave");
        bob.click('C'); // confirmation screen
        assertNotNull(latest(HistoryType.LEAVE));
        assertEquals("Bob", latest(HistoryType.LEAVE).actorName());

        join(carl);
        s.clan(alice, "transfer", "Carl");
        if (alice.menu != null) alice.click('C');
        Database.HistoryRow leader = latest(HistoryType.LEADER);
        assertNotNull(leader, all().toString());
        assertEquals("Alice", leader.actorName());
        assertEquals("Carl", leader.targetName());
        assertEquals(0, leader.amount(), "done by the Minh Chủ, not staff");
    }

    @Test
    void staffActionsHaveNoActor() throws Exception {
        join(bob);
        s.admin(s.console, "kick", "Bob");
        Database.HistoryRow kick = latest(HistoryType.KICK);
        assertEquals("", kick.actorName());
        assertEquals("Bob", kick.targetName());
        s.admin(s.console, "icon", "set", "Alpha", "blank_gold");
        assertEquals(HistoryType.SEPARATOR + "blank_gold", latest(HistoryType.ICON).detail());
    }

    @Test
    void filterByGroupAndPaging() throws Exception {
        join(bob);
        s.admin(s.console, "minhhuy", "give", "clan", "Alpha", "5", "-s");
        s.admin(s.console, "minhhuy", "give", "clan", "Alpha", "6", "-s");
        List<String> minhHuy = HistoryType.of(HistoryType.Group.MINHHUY).stream().map(HistoryType::key).toList();
        Database.HistoryPage page = s.db.historyPage(alpha.uuid(), minhHuy, 0, 1).get(5, TimeUnit.SECONDS);
        assertEquals(2, page.total());
        assertEquals(1, page.rows().size());
        assertEquals(6, page.rows().get(0).amount(), "newest first");
        List<String> members = HistoryType.of(HistoryType.Group.MEMBERS).stream().map(HistoryType::key).toList();
        assertTrue(s.db.historyPage(alpha.uuid(), members, 0, 10).get(5, TimeUnit.SECONDS).rows().stream()
                .allMatch(r -> HistoryType.of(r.type()).group() == HistoryType.Group.MEMBERS));
    }

    @Test
    void oldRowsArePrunedAndADisbandedClanLosesItsHistory() throws Exception {
        long now = System.currentTimeMillis();
        s.db.addHistory(new Database.HistoryRow(0, alpha.uuid(), now - 31L * 86_400_000L, "JOIN", null, "Old", null, "",
                0, ""));
        assertEquals(2, all().size());
        s.db.pruneHistory(now - 30L * 86_400_000L);
        assertEquals(1, all().size(), "only the 30-day-old row is gone");

        s.clan(alice, "disband");
        if (alice.menu != null) alice.click('C');
        assertEquals(0, all().size());
    }

    @Test
    void anOldDatabaseIsMigrated() throws Exception {
        File file = new File(dir, "old.db");
        UUID clan = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
             Statement st = c.createStatement()) {
            // the 1.1.0 schema: no minh_huy, no clan_history
            st.execute("CREATE TABLE clans (uuid TEXT PRIMARY KEY, key TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                    + "name TEXT NOT NULL, flag TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '', "
                    + "leader TEXT NOT NULL, created_at INTEGER NOT NULL, friendly_fire INTEGER NOT NULL DEFAULT 0, "
                    + "points INTEGER NOT NULL DEFAULT 0, max_members INTEGER NOT NULL, last_rename INTEGER NOT NULL DEFAULT 0, "
                    + "icon TEXT NOT NULL DEFAULT '', owned_icons TEXT NOT NULL DEFAULT '')");
            st.execute("INSERT INTO clans (uuid, key, name, leader, created_at, points, max_members) VALUES ('" + clan
                    + "', 'Old', 'Old', '" + leader + "', 1, 42, 8)");
        }
        Database db = Database.open(file, Logger.getLogger("test"));
        assertNotNull(db);
        try {
            List<ClanData> loaded = db.loadClans(com.dex.boxclans.text.ClanNameFormatter::render);
            assertEquals(1, loaded.size());
            assertEquals(42, loaded.get(0).points(), "old data kept");
            assertEquals(0, loaded.get(0).minhHuy());
            assertEquals(0, db.historyPage(clan, List.of(), 0, 10).get(5, TimeUnit.SECONDS).total());
        } finally {
            db.close();
        }
    }
}
