package com.dex.boxclans;

import com.dex.boxclans.FakeServer.FakePlayer;
import com.dex.boxclans.api.event.ClanIconChangeEvent;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.hook.PlaceholderValues;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.ClanNameFormatter;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BOXCLANS_ICON_SPEC.md: placeholders, Shop Clan, /clanadmin icon, storage. */
class IconTest {

    /** The n-th icon slot of the open menu. */
    private int slot(FakePlayer p, int n) {
        return com.dex.boxclans.gui.MenuAccess.slots(p.menu, 'X').get(n);
    }

    @TempDir
    File dir;
    FakeServer s;
    PlaceholderValues v;
    FakePlayer a;
    FakePlayer b;
    FakePlayer loner;
    ClanData rong;

    @BeforeEach
    void setUp() {
        s = new FakeServer(dir);
        v = new PlaceholderValues(s.plugin);
        a = s.join("Alice");
        b = s.join("Bob");
        loner = s.join("Eve");
        s.clan(a, "create");
        s.type(a, "&cRồng Đỏ");
        a.click('C');
        rong = s.manager.of(a.id);
        s.clan(a, "invite", "Bob");
        s.clan(b, "accept");
    }

    @AfterEach
    void tearDown() {
        s.close();
    }

    @Test
    void noIconLeavesNoSpace() {
        assertEquals("", rong.icon(), "default-icon: none");
        assertEquals("", v.resolve(a.id, "icon"));
        assertEquals("", v.resolve(a.id, "icon_static"));
        assertEquals("none", v.resolve(a.id, "icon_id"));
        assertEquals("", v.resolve(loner.id, "icon"));
        assertEquals("", v.resolve(loner.id, "icon_static"));
        assertEquals("", v.resolve(null, "top_9_icon"), "missing clan: empty, not the none-value");
        assertEquals("", v.resolve(null, "clan_Nobody_icon"));
    }

    @Test
    void animatedFramesFollowTheClock() {
        s.admin(s.console, "icon", "set", "RongDo", "royal");
        assertEquals("royal", rong.icon());
        s.iconClock.set(0);
        assertEquals("§f<shieldranks:royal_1> ", v.resolve(a.id, "icon"));
        s.iconClock.set(150);
        assertEquals("§f<shieldranks:royal_2> ", v.resolve(b.id, "icon"));
        s.iconClock.set(2_799);
        assertEquals("§f<shieldranks:royal_28> ", v.resolve(a.id, "icon"));
        s.iconClock.set(2_800);
        assertEquals("§f<shieldranks:royal_1> ", v.resolve(a.id, "icon"), "28 frames, then loops");
        assertEquals("§f<shieldranks:royal_static> ", v.resolve(a.id, "icon_static"));
        assertEquals("royal", v.resolve(a.id, "icon_id"));
        assertEquals("§f<shieldranks:royal_1> ", v.resolve(null, "top_1_icon"));
        assertEquals("§f<shieldranks:royal_1> ", v.resolve(null, "clan_RongDo_icon"));
        // 25-frame icon wraps at 25
        s.admin(s.console, "icon", "set", "RongDo", "diamond");
        s.iconClock.set(2_500);
        assertEquals("§f<shieldranks:diamond_1> ", v.resolve(a.id, "icon"));
    }

    @Test
    void leaderChoosesInTheIconMenu() {
        // 1.3.0: icons are equipped in Cài đặt > Icon bang hội; the default ones are there without buying
        s.clan(a, "settings");
        a.click('I');
        assertEquals("Icon bang hội", FakeServer.TITLES.get(a.menu));
        a.clickSlot(slot(a, 1), ClickType.LEFT);
        assertEquals("blank_gold", rong.icon());
        assertEquals(Set.of("blank_gold"), rong.ownedIcons());
        assertTrue(b.got("Alice đã đổi icon bang hội thành"), b.messages.toString());
        assertTrue(FakeServer.EVENTS.stream().anyMatch(e -> e instanceof ClanIconChangeEvent c
                && c.oldIcon().isEmpty() && c.newIcon().equals("blank_gold") && a.id.equals(c.actor())));

        a.clickSlot(slot(a, 0), ClickType.LEFT);
        assertEquals("blank_silver", rong.icon());
        assertEquals(Set.of("blank_gold", "blank_silver"), rong.ownedIcons(), "owned icons stay owned");

        a.click('R');
        assertEquals("", rong.icon());
        assertEquals("", v.resolve(a.id, "icon"));
        assertTrue(b.got("Alice đã gỡ icon bang hội."));
    }

    @Test
    void shopCommand() {
        s.clan(a, "shop");
        assertEquals("Cửa hàng bang hội", FakeServer.TITLES.get(a.menu));
        loner.clear();
        s.clan(loner, "shop");
        assertTrue(loner.menu == null, "no clan: no shop");
        assertFalse(loner.messages.isEmpty(), "no clan: error message");
    }

    @Test
    void onlyTheLeaderAndTheViceChange() {
        s.plugin.guis().openIcons(b.player);
        b.clickSlot(slot(b, 0), ClickType.LEFT);
        assertEquals("", rong.icon());
        assertTrue(b.got("Chỉ Minh Chủ và Phó Bang mới đổi được icon bang hội."), b.messages.toString());
        s.admin(s.console, "icon", "set", "RongDo", "royal");
        b.click('R');
        assertEquals("royal", rong.icon());
        // a Phó Bang may (1.3.0)
        s.clan(a, "promote", "Bob");
        s.plugin.guis().openIcons(b.player);
        b.clickSlot(slot(b, 2), ClickType.LEFT);
        assertEquals("blank_purple", rong.icon());
    }

    @Test
    void anIconNotOwnedCannotBeEquipped() {
        s.plugin.actions().chooseIcon(a.player, "dragon");
        assertEquals("", rong.icon());
        assertTrue(a.got("Bang hội chưa có icon"), a.messages.toString());
        s.admin(s.console, "icon", "unlock", "RongDo", "dragon");
        s.plugin.actions().chooseIcon(a.player, "dragon");
        assertEquals("dragon", rong.icon());
    }

    @Test
    void adminCommands() {
        s.admin(s.console, "icon", "unlock", "RongDo", "dragon");
        assertEquals(Set.of("dragon"), rong.ownedIcons());
        assertEquals("", rong.icon(), "unlock does not use it");
        s.admin(s.console, "icon", "set", "RongDo", "dragon");
        assertEquals("dragon", rong.icon());
        s.admin(s.console, "icon", "lock", "RongDo", "dragon");
        assertEquals("", rong.icon(), "taking back the icon in use removes it");
        assertFalse(rong.ownedIcons().contains("dragon"));
        FakeServer.CONSOLE.clear();
        s.admin(s.console, "icon", "set", "RongDo", "nope");
        assertTrue(FakeServer.CONSOLE.stream().anyMatch(l -> l.contains("Không có icon nope")), FakeServer.CONSOLE.toString());
        s.admin(s.console, "icon", "set", "RongDo", "royal");
        s.admin(s.console, "icon", "set", "RongDo", "none");
        assertEquals("", rong.icon());
        assertEquals(List.of("set", "unlock", "lock", "list"),
                s.admin.onTabComplete(s.console, s.command, "clanadmin", new String[]{"icon", ""}));
        assertTrue(s.admin.onTabComplete(s.console, s.command, "clanadmin", new String[]{"icon", "set", "RongDo", "ro"})
                .contains("royal"));
    }

    @Test
    void iconsAreStored() throws Exception {
        s.admin(s.console, "icon", "unlock", "RongDo", "coins");
        s.admin(s.console, "icon", "set", "RongDo", "royal");
        List<ClanData> loaded = s.db.loadClans(ClanNameFormatter::render);
        ClanData back = loaded.stream().filter(c -> c.uuid().equals(rong.uuid())).findFirst().orElseThrow();
        assertEquals("royal", back.icon());
        assertEquals(Set.of("coins", "royal"), back.ownedIcons());
    }

    @Test
    void oldDatabaseGetsTheColumns(@TempDir File other) throws Exception {
        File file = new File(other, "clans.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
             Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE clans (uuid TEXT PRIMARY KEY, key TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        name TEXT NOT NULL, flag TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '',
                        leader TEXT NOT NULL, created_at INTEGER NOT NULL, friendly_fire INTEGER NOT NULL DEFAULT 0,
                        points INTEGER NOT NULL DEFAULT 0, max_members INTEGER NOT NULL,
                        last_rename INTEGER NOT NULL DEFAULT 0)""");
            st.execute("INSERT INTO clans (uuid, key, name, leader, created_at, max_members) VALUES "
                    + "('00000000-0000-0000-0000-000000000001', 'Cu', 'Cũ', '00000000-0000-0000-0000-000000000002', 1, 8)");
        }
        Database db = Database.open(file, Logger.getLogger("test"));
        try {
            List<ClanData> loaded = db.loadClans(ClanNameFormatter::render);
            assertEquals(1, loaded.size(), "old rows survive the migration");
            assertEquals("", loaded.get(0).icon());
            assertTrue(loaded.get(0).ownedIcons().isEmpty());
        } finally {
            db.close();
        }
    }
}
