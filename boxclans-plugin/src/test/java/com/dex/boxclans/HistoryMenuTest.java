package com.dex.boxclans;

import com.dex.boxclans.FakeServer.FakePlayer;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.gui.ClanHistoryMenu;
import com.dex.boxclans.gui.ClanMenu;
import com.dex.boxclans.gui.ClanVaultMenu;
import com.dex.boxclans.gui.MenuAccess;
import com.dex.boxclans.gui.MenuItems;
import com.dex.boxclans.history.HistoryFormat;
import com.dex.boxclans.history.HistoryType;
import com.dex.boxclans.item.Delivery;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.4.0 (BOXCLANS_SHOP_SPEC.md §6.2): the history menu, its filters and paging, and what each sheet says.
 * 1.4.2: every member reads the history; members only look at the storage.
 */
class HistoryMenuTest {

    @TempDir
    File dir;
    FakeServer s;
    FakePlayer alice;
    FakePlayer bob;
    FakePlayer carl;
    FakePlayer staff;
    ClanData alpha;

    @BeforeEach
    void setUp() {
        s = new FakeServer(dir);
        alice = s.join("Alice");
        bob = s.join("Bob");
        carl = s.join("Carl");
        staff = s.join("Staff", "boxclans.admin");
        s.clan(alice, "create");
        s.type(alice, "Alpha");
        alice.click('C');
        alpha = s.manager.of(alice.id);
        for (FakePlayer p : List.of(bob, carl)) {
            s.clan(alice, "invite", p.name);
            s.clan(p, "accept");
        }
        s.clan(alice, "promote", "Carl");
    }

    @AfterEach
    void tearDown() {
        s.close();
    }

    /**
     * Waits for the page read off the main thread. The fake scheduler runs the answer on the database thread, so a
     * second read through the same queue also waits for the redraw that follows it (a click during the redraw is lost).
     */
    private Database.HistoryPage loaded(FakePlayer p) throws Exception {
        ClanHistoryMenu menu = assertInstanceOf(ClanHistoryMenu.class, p.menu);
        long deadline = System.currentTimeMillis() + 5000;
        while (menu.result() == null && System.currentTimeMillis() < deadline) Thread.sleep(10);
        s.db.historyPage(alpha.uuid(), List.of(), 0, 1).get(5, java.util.concurrent.TimeUnit.SECONDS);
        return menu.result();
    }

    private void row(String type, long amount, String detail) {
        s.db.addHistory(new Database.HistoryRow(0, alpha.uuid(), System.currentTimeMillis(), type, null, "console", null,
                "", amount, detail));
    }

    @Test
    void everyMemberOpensIt() throws Exception {
        s.clan(bob);
        assertEquals(Material.WRITABLE_BOOK, MenuAccess.item(bob.menu, MenuAccess.slots(bob.menu, 'G').get(0)).getType());
        bob.click('G');
        Database.HistoryPage page = loaded(bob);
        // create, 2 joins, 1 role change
        assertEquals(4, page.total(), page.rows().toString());
        assertEquals("ROLE", page.rows().get(0).type(), "newest first");
        bob.click('B');
        assertInstanceOf(ClanMenu.class, bob.menu, "back to /clan");

        s.clan(carl, "lichsu");
        assertEquals(4, loaded(carl).total());

        FakePlayer dan = s.join("Dan");
        s.clan(dan, "lichsu");
        assertFalse(dan.menu instanceof ClanHistoryMenu);
        assertTrue(dan.got("Bạn chưa có bang hội."), dan.messages.toString());
    }

    @Test
    void membersOnlyLookAtTheStorage() {
        s.vault.add(alpha.uuid(), "key", Delivery.ITEM, FakeServer.CODEC.encode(MenuItems.stack(Material.TRIPWIRE_HOOK, 1)),
                List.of(), 2);
        s.clan(bob);
        bob.click('K');
        assertInstanceOf(ClanVaultMenu.class, bob.menu);
        bob.clear();
        bob.click('X');
        bob.click('X', ClickType.RIGHT);
        assertEquals(2, s.vault.items(alpha.uuid(), null, x -> x).get(0).amount(), "a member's click takes nothing");
        assertTrue(bob.messages.isEmpty(), "and says nothing: " + bob.messages);
        bob.click('B');
        assertInstanceOf(ClanMenu.class, bob.menu);

        s.clan(carl, "kho");
        carl.click('X');
        assertEquals(1, s.vault.items(alpha.uuid(), null, x -> x).get(0).amount(), "the Phó Bang still withdraws");
    }

    @Test
    void storageHidesHowToUseLines() {
        List<Component> lore = List.of(Component.text("Boost cho cả bang"), Component.text(""),
                Component.text("Chỉ Minh Chủ, Phó Bang dùng được"),
                Component.text("[Chuột phải để kích hoạt]").color(NamedTextColor.GREEN), Component.text(""));
        List<String> left = MenuAccess.withoutUseLines(lore, List.of("để kích hoạt", "CHUỘT TRÁI", " ")).stream()
                .map(Text::plain).toList();
        assertEquals(List.of("Boost cho cả bang", "", "Chỉ Minh Chủ, Phó Bang dùng được"), left);
        assertEquals(4, MenuAccess.withoutUseLines(lore, List.of()).size(), "without a list only the last blank line goes");
        List<String> configured = s.plugin.menus().menu("clan-vault").getStringList("hide-lore-containing");
        assertTrue(configured.contains("để kích hoạt"), configured.toString());
    }

    @Test
    void filterAndPages() throws Exception {
        for (int i = 0; i < 40; i++) row("MINHHUY_ADD", i + 1, "Event " + i);
        s.clan(alice, "lichsu");
        assertEquals(44, loaded(alice).total());
        assertEquals(21, loaded(alice).rows().size(), "21 sheets per page (1.4.3: 3 rows of 7)");

        // tab 3 = MINHHUY (after "Tất cả" and MEMBERS)
        alice.clickSlot(MenuAccess.slots(alice.menu, 'T').get(2), ClickType.LEFT);
        Database.HistoryPage minhHuy = loaded(alice);
        assertEquals(40, minhHuy.total());
        assertTrue(minhHuy.rows().stream().allMatch(r -> r.type().equals("MINHHUY_ADD")));
        alice.click('N');
        Database.HistoryPage second = loaded(alice);
        assertEquals(19, second.rows().size(), "40 = 21 + 19");
        assertEquals(19, second.rows().get(0).amount(), "older ones on the second page");

        // tab 2 = MEMBERS
        alice.clickSlot(MenuAccess.slots(alice.menu, 'T').get(1), ClickType.LEFT);
        Database.HistoryPage members = loaded(alice);
        assertEquals(3, members.total());
        assertTrue(members.rows().stream().allMatch(r -> HistoryType.of(r.type()).group() == HistoryType.Group.MEMBERS));
    }

    @Test
    void historyLayoutAndMarkers() throws Exception {
        s.clan(alice, "lichsu");
        loaded(alice);
        List<Integer> marks = MenuAccess.slots(alice.menu, 'U');
        assertEquals(7, marks.size());
        assertEquals(Material.LIME_STAINED_GLASS_PANE, MenuAccess.item(alice.menu, marks.get(0)).getType());
        alice.clickSlot(MenuAccess.slots(alice.menu, 'T').get(3), ClickType.LEFT);
        loaded(alice);
        assertEquals(Material.LIME_STAINED_GLASS_PANE, MenuAccess.item(alice.menu, marks.get(3)).getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, MenuAccess.item(alice.menu, marks.get(0)).getType());
        int clock = MenuAccess.slots(alice.menu, 'I').get(0);
        assertEquals(49, clock, "page clock in the middle of the last row");
        assertEquals(48, MenuAccess.slots(alice.menu, 'P').get(0));
        assertEquals(50, MenuAccess.slots(alice.menu, 'N').get(0));
    }

    @Test
    void staffReadAnyClan() throws Exception {
        s.admin(staff.player, "history", "Alpha");
        assertEquals(4, loaded(staff).total());
    }

    // ------------------------------------------------------------------ sheets

    private List<String> sheet(String type, UUID actor, String actorName, String targetName, long amount, String detail) {
        HistoryFormat f = new HistoryFormat(s.plugin.menus().menu("clan-history"), s.plugin.messages(), s.plugin.roles(),
                s.plugin.icons(), ZoneId.of("Asia/Ho_Chi_Minh"));
        long at = ZonedDateTime.of(2026, 10, 23, 21, 5, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli();
        HistoryFormat.Sheet sh = f.render(new Database.HistoryRow(1, alpha.uuid(), at, type, actor, actorName, null,
                targetName, amount, detail));
        List<String> out = new java.util.ArrayList<>();
        out.add(Text.plain(Text.parse(sh.name())));
        for (String l : sh.lore()) out.add(Text.plain(Text.parse(l)));
        return out;
    }

    @Test
    void eachSheetSaysWhatHappened() {
        assertEquals(List.of("Đổi vai trò", "Người chơi: Bob", "Từ Thành Viên thành Phó Bang", "Người đổi: Alice", "",
                "Thời gian: 23/10/2026 21:05"), sheet("ROLE", alice.id, "Alice", "Bob", 0, "ThanhVien>PhoBang"));
        assertEquals(List.of("Bị kick", "Người bị kick: Bob", "Người kick: Quản trị viên", "", "Thời gian: 23/10/2026 21:05"),
                sheet("KICK", null, "", "Bob", 0, ""));
        assertEquals("Tên cũ: Alpha", sheet("RENAME", alice.id, "Alice", "", 0, "Alpha" + HistoryType.SEPARATOR + "&cBeta").get(1));
        assertEquals("Tên mới: Beta", sheet("RENAME", alice.id, "Alice", "", 0, "Alpha" + HistoryType.SEPARATOR + "&cBeta").get(2));
        assertEquals("Icon mới: Khiên Rồng", sheet("ICON", alice.id, "Alice", "", 0, HistoryType.SEPARATOR + "dragon").get(2));
        assertEquals("Icon cũ: Không có", sheet("ICON", alice.id, "Alice", "", 0, HistoryType.SEPARATOR + "dragon").get(1));
        assertEquals(List.of("Mua trong cửa hàng", "Vật phẩm: Tripwire Hook", "Giá: 1.500 Minh Huy", "Người mua: Carl", "",
                "Thời gian: 23/10/2026 21:05"), sheet("SHOP_BUY", carl.id, "Carl", "", 1500, "Tripwire Hook"));
        assertEquals(List.of("Thay boost bang hội", "Boost: mining x2 thành x3", "Thời gian: 1 giờ", "Người dùng: Alice", "",
                "Thời gian: 23/10/2026 21:05"),
                sheet("BOOST_REPLACE", null, "Alice", "", 30, "mining" + HistoryType.SEPARATOR + "3600" + HistoryType.SEPARATOR + "20"));
        assertEquals("Boost: quest x1.5", sheet("BOOST_END", null, "", "", 15, "quest" + HistoryType.SEPARATOR + "0"
                + HistoryType.SEPARATOR + "0").get(1));
        assertEquals("(do quản trị viên chuyển)", sheet("LEADER", alice.id, "Alice", "Carl", 1, "").get(3));
        assertEquals("Đã bật", sheet("PVP", alice.id, "Alice", "", 1, "").get(1));
        assertEquals("FUTURE_THING", sheet("FUTURE_THING", null, "", "", 0, "x").get(0), "a type from a newer version");
    }
}
