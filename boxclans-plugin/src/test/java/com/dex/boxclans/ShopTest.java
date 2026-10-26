package com.dex.boxclans;

import com.dex.boxclans.FakeServer.FakePlayer;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.config.ShopConfig;
import com.dex.boxclans.gui.ClanShopMenu;
import com.dex.boxclans.gui.ClanVaultMenu;
import com.dex.boxclans.gui.ConfirmMenu;
import com.dex.boxclans.gui.MenuAccess;
import com.dex.boxclans.gui.MenuItems;
import com.dex.boxclans.history.HistoryType;
import com.dex.boxclans.hook.ClanBoosts;
import com.dex.boxclans.item.Delivery;
import com.dex.boxclans.shop.ShopEntry;
import com.dex.boxclans.shop.ShopService;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.vault.VaultItem;
import com.dex.boxclans.vault.VaultService;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.3.0 (BOXCLANS_SHOP_SPEC.md §3 - §5): shop, storage, icons in the shop, clan boosts, staff commands. */
class ShopTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @TempDir
    File dir;
    FakeServer s;
    FakePlayer alice;   // Minh Chủ
    FakePlayer bob;     // Thành Viên
    FakePlayer carl;    // Phó Bang
    FakePlayer staff;
    ClanData alpha;

    @BeforeEach
    void setUp() {
        s = new FakeServer(dir);
        // Wednesday 22/10/2026 12:00 in Vietnam
        s.now = ZonedDateTime.of(2026, 10, 21, 12, 0, 0, 0, VN).toInstant().toEpochMilli();
        alice = s.join("Alice");
        bob = s.join("Bob");
        carl = s.join("Carl");
        staff = s.join("Staff", "boxclans.admin.shop", "boxclans.admin");
        s.clan(alice, "create");
        s.type(alice, "Alpha");
        alice.click('C');
        alpha = s.manager.of(alice.id);
        for (FakePlayer p : List.of(bob, carl)) {
            s.clan(alice, "invite", p.name);
            s.clan(p, "accept");
        }
        s.clan(alice, "promote", "Carl");
        FakeServer.COMMANDS.clear();
    }

    @AfterEach
    void tearDown() {
        s.close();
    }

    private static ItemStack item(Material m) {
        return MenuItems.stack(m, 1);
    }

    private ShopEntry entry(Material m, String category, long price, int limit) {
        return s.shop.add(category, FakeServer.CODEC.encode(item(m)), price, limit);
    }

    private void money(long n) {
        s.minhHuy.give(alpha, n, "test", "test", true);
    }

    private List<Database.HistoryRow> history(HistoryType type) throws Exception {
        return s.db.historyPage(alpha.uuid(), List.of(type.key()), 0, 100).get(5, TimeUnit.SECONDS).rows();
    }

    // ------------------------------------------------------------------ buying (§4.4)

    @Test
    void theLeaderBuysThroughTheConfirmationScreen() throws Exception {
        ShopEntry key = entry(Material.TRIPWIRE_HOOK, "key", 300, 0);
        money(1000);
        s.plugin.shopActions().askBuy(alice.player, key.id(), () -> { });
        assertInstanceOf(ConfirmMenu.class, alice.menu);
        assertEquals(1000, alpha.minhHuy(), "nothing before the confirmation");
        alice.click('C');
        assertEquals(700, alpha.minhHuy());
        List<VaultItem> stored = s.vault.items(alpha.uuid(), null, id -> id);
        assertEquals(1, stored.size());
        assertEquals("key", stored.get(0).category());
        assertEquals(1, stored.get(0).amount());
        assertTrue(bob.got("Alice (Minh Chủ) đã mua Tripwire Hook với 300 Minh Huy. Còn lại: 700"), bob.messages.toString());
        Database.HistoryRow row = history(HistoryType.SHOP_BUY).get(0);
        assertEquals("Alice", row.actorName());
        assertEquals(300, row.amount());

        // the Phó Bang may buy too; a second one merges into the same stack
        assertTrue(s.plugin.shopActions().buy(carl.player, key.id(), 300));
        assertEquals(2, s.vault.items(alpha.uuid(), null, id -> id).get(0).amount());
    }

    @Test
    void membersNotEnoughMoneyAndStalePricesAreRefused() {
        ShopEntry key = entry(Material.TRIPWIRE_HOOK, "key", 300, 0);
        money(200);
        assertFalse(s.plugin.shopActions().buy(alice.player, key.id(), 300));
        assertTrue(alice.got("Bang hội chỉ có 200 Minh Huy, cần 300."), alice.messages.toString());
        money(800);
        assertFalse(s.plugin.shopActions().buy(bob.player, key.id(), 300));
        assertTrue(bob.got("Chỉ Minh Chủ và Phó Bang được mua"), bob.messages.toString());
        s.plugin.shopActions().askBuy(bob.player, key.id(), () -> { });
        assertNull(bob.menu, "a member never gets the confirmation screen");

        // staff changes the price while the screen is open
        s.plugin.shopActions().askBuy(alice.player, key.id(), () -> { });
        s.shop.save(key.withPrice(50));
        alice.click('C');
        assertEquals(1000, alpha.minhHuy(), "nothing taken at the old price");
        assertTrue(alice.got("Giá vừa thay đổi"), alice.messages.toString());
        // hidden or removed: not for sale
        s.shop.save(s.shop.entry(key.id()).withHidden(true));
        assertFalse(s.plugin.shopActions().buy(alice.player, key.id(), 50));
        s.shop.remove(key.id());
        assertFalse(s.plugin.shopActions().buy(alice.player, key.id(), 50));
        assertEquals(1000, alpha.minhHuy());
    }

    @Test
    void weeklyLimitResetsOnMondayMidnight() {
        ShopEntry boost = entry(Material.HONEY_BOTTLE, "boost", 10, 2);
        money(1000);
        assertTrue(s.plugin.shopActions().buy(alice.player, boost.id(), 10));
        assertTrue(s.plugin.shopActions().buy(carl.player, boost.id(), 10));
        assertFalse(s.plugin.shopActions().buy(alice.player, boost.id(), 10));
        assertTrue(alice.got("đủ 2 lần trong tuần này"), alice.messages.toString());
        // Sunday 23:59 still the same week
        s.now = ZonedDateTime.of(2026, 10, 25, 23, 59, 0, 0, VN).toInstant().toEpochMilli();
        assertFalse(s.plugin.shopActions().buy(alice.player, boost.id(), 10));
        // Monday 00:00 Vietnam time: a new week
        s.now = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, VN).toInstant().toEpochMilli();
        assertTrue(s.plugin.shopActions().buy(alice.player, boost.id(), 10));
        assertEquals(1000 - 30, alpha.minhHuy());
    }

    @Test
    void weekStartFollowsTheConfig() {
        ShopConfig cfg = s.plugin.shopConfig();
        long monday = ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, VN).toInstant().toEpochMilli();
        assertEquals(monday, cfg.weekStart(ZonedDateTime.of(2026, 10, 21, 12, 0, 0, 0, VN).toInstant().toEpochMilli()));
        assertEquals(monday, cfg.weekStart(monday));
        assertEquals(monday - 7 * 86_400_000L, cfg.weekStart(monday - 1));
    }

    @Test
    void countersAndEntriesSurviveARestart() throws Exception {
        ShopEntry boost = entry(Material.HONEY_BOTTLE, "boost", 10, 1);
        money(100);
        assertTrue(s.plugin.shopActions().buy(alice.player, boost.id(), 10));
        ShopService again = new ShopService(s.db, s.plugin::shopConfig, () -> s.now);
        again.load();
        assertEquals(1, again.bought(alpha.uuid(), boost.id()));
        assertEquals(10, again.entry(boost.id()).price());
        VaultService vault = new VaultService(s.db, () -> s.now);
        vault.load();
        VaultItem v = vault.items(alpha.uuid(), null, id -> id).get(0);
        assertEquals("HONEY_BOTTLE", new String(v.item(), java.nio.charset.StandardCharsets.UTF_8),
                "the stored bytes come back unchanged");
    }

    // ------------------------------------------------------------------ icons in the shop (§3, §4.4)

    @Test
    void iconsAreBoughtNotEquipped() throws Exception {
        money(2000);
        assertFalse(s.plugin.shopActions().buyIcon(alice.player, "blank_gold", 0), "default icons are not sold");
        assertTrue(s.plugin.shopActions().buyIcon(carl.player, "dragon", 1500));
        assertTrue(alpha.ownedIcons().contains("dragon"));
        assertEquals("", alpha.icon(), "not equipped");
        assertEquals(500, alpha.minhHuy());
        assertTrue(carl.got("Icon chưa được trang bị"), carl.messages.toString());
        assertFalse(s.plugin.shopActions().buyIcon(alice.player, "dragon", 1500), "already owned");
        assertFalse(s.plugin.shopActions().buyIcon(bob.player, "coins", 300), "members cannot buy");
        assertEquals(1, history(HistoryType.SHOP_BUY).size());
    }

    // ------------------------------------------------------------------ storage (§5)

    @Test
    void withdrawNeedsRoomAndTheRole() throws Exception {
        s.vault.add(alpha.uuid(), "key", Delivery.ITEM, FakeServer.CODEC.encode(item(Material.TRIPWIRE_HOOK)), List.of(), 2);
        long id = s.vault.items(alpha.uuid(), null, x -> x).get(0).id();
        assertFalse(s.plugin.shopActions().withdraw(bob.player, id));
        assertTrue(bob.got("Chỉ Minh Chủ và Phó Bang được rút"), bob.messages.toString());

        for (int i = 0; i < 36; i++) carl.storage[i] = item(Material.STONE);
        assertFalse(s.plugin.shopActions().withdraw(carl.player, id));
        assertTrue(carl.got("Túi đồ đầy"), carl.messages.toString());
        assertEquals(2, s.vault.item(alpha.uuid(), id).amount(), "the storage is not touched");

        carl.storage[5] = null;
        assertTrue(s.plugin.shopActions().withdraw(carl.player, id));
        assertEquals(Material.TRIPWIRE_HOOK, carl.storage[5].getType());
        assertEquals(1, s.vault.item(alpha.uuid(), id).amount());
        assertTrue(alice.got("Carl (Phó Bang) đã rút Tripwire Hook khỏi kho bang hội."), alice.messages.toString());
        assertEquals(1, history(HistoryType.VAULT_WITHDRAW).size());
    }

    @Test
    void commandItemsRunTheirCommandsForTheWithdrawer() {
        ShopEntry key = entry(Material.TRIPWIRE_HOOK, "key", 10, 0);
        assertNull(s.plugin.shopEditor().addCommand(key.id(), "/crate key give {player} vote {amount}"));
        assertNull(s.plugin.shopEditor().addCommand(key.id(), "say {clan}"));
        assertEquals(Delivery.COMMAND, s.shop.entry(key.id()).delivery());
        money(100);
        assertTrue(s.plugin.shopActions().buy(alice.player, key.id(), 10));
        // staff edits the entry afterwards: the stored copy keeps the old commands
        s.plugin.shopEditor().removeCommand(key.id(), 1);
        s.shop.remove(key.id());
        long id = s.vault.items(alpha.uuid(), null, x -> x).get(0).id();
        for (int i = 0; i < 36; i++) alice.storage[i] = item(Material.STONE);   // a command needs no room
        assertTrue(s.plugin.shopActions().withdraw(alice.player, id));
        assertEquals(List.of("crate key give Alice vote 1", "say Alpha"), FakeServer.COMMANDS);
        assertTrue(s.vault.items(alpha.uuid(), null, x -> x).isEmpty());
    }

    @Test
    void storageMenuMembersOnlyLook() {
        s.vault.add(alpha.uuid(), "key", Delivery.ITEM, FakeServer.CODEC.encode(item(Material.TRIPWIRE_HOOK)), List.of(), 1);
        s.clan(bob, "kho");
        assertInstanceOf(ClanVaultMenu.class, bob.menu);
        bob.clear();
        bob.clickSlot(MenuAccess.slots(bob.menu, 'X').get(0), ClickType.LEFT);
        assertTrue(bob.messages.isEmpty(), "1.4.2: a member's click does nothing: " + bob.messages);
        assertEquals(1, s.vault.count(alpha.uuid()));
        s.clan(alice, "kho");
        alice.clickSlot(MenuAccess.slots(alice.menu, 'X').get(0), ClickType.LEFT);
        assertEquals(0, s.vault.count(alpha.uuid()));
        assertEquals(Material.TRIPWIRE_HOOK, alice.items().get(0).getType());
    }

    // ------------------------------------------------------------------ clan boost bottles (§5.3)

    private long bottle() {
        s.vault.add(alpha.uuid(), "boost", Delivery.ITEM, FakeServer.CODEC.encode(item(Material.HONEY_BOTTLE)), List.of(), 3);
        return s.vault.items(alpha.uuid(), null, x -> x).get(0).id();
    }

    @Test
    void aClanBottleIsUsedFromTheStorage() throws Exception {
        long id = bottle();
        assertTrue(s.plugin.shopActions().use(carl.player, id, false, () -> { }));
        assertEquals(List.of("start mining 20 3600 false Carl"), s.boosts.calls);
        assertEquals(2, s.vault.item(alpha.uuid(), id).amount());
        assertTrue(bob.got("Carl (Phó Bang) đã dùng Honey Bottle từ kho bang hội."), bob.messages.toString());
        assertEquals(1, history(HistoryType.VAULT_USE).size());

        // a replacement asks first, the bottle is used once confirmed
        s.boosts.next.add(ClanBoosts.Status.CONFIRM_REPLACE);
        assertFalse(s.plugin.shopActions().use(alice.player, id, false, () -> { }));
        assertInstanceOf(ConfirmMenu.class, alice.menu);
        assertEquals(2, s.vault.item(alpha.uuid(), id).amount());
        alice.click('C');
        assertEquals("start mining 20 3600 true Alice", s.boosts.calls.get(2));
        assertEquals(1, s.vault.item(alpha.uuid(), id).amount());

        // refused: the bottle stays
        s.boosts.next.add(ClanBoosts.Status.LOWER);
        assertFalse(s.plugin.shopActions().use(alice.player, id, true, () -> { }));
        assertEquals(1, s.vault.item(alpha.uuid(), id).amount());
        assertTrue(alice.got("thấp hơn nên không dùng được"), alice.messages.toString());
        assertFalse(s.plugin.shopActions().use(bob.player, id, true, () -> { }), "members cannot use");
    }

    @Test
    void withoutBoxCoreTheBottleIsOnlyWithdrawn() {
        long id = bottle();
        s.boostsInstalled = false;
        assertFalse(s.plugin.shopActions().use(alice.player, id, false, () -> { }));
        assertTrue(alice.got("Boost bang hội chưa hoạt động"), alice.messages.toString());
        assertEquals(3, s.vault.item(alpha.uuid(), id).amount());
        assertTrue(s.plugin.shopActions().withdraw(alice.player, id));
        assertEquals(Material.HONEY_BOTTLE, alice.items().get(0).getType());
    }

    @Test
    void leftClickUsesABottleRightClickDoesNothing() throws Exception {
        long id = bottle();
        s.clan(alice, "kho");
        int slot = MenuAccess.slots(alice.menu, 'X').get(0);
        alice.clickSlot(slot, ClickType.LEFT);
        assertEquals(1, s.boosts.calls.size());
        assertEquals(2, s.vault.item(alpha.uuid(), id).amount());
        s.clan(alice, "kho");
        Thread.sleep(300); // the double-click guard
        alice.clickSlot(MenuAccess.slots(alice.menu, 'X').get(0), ClickType.RIGHT);
        assertEquals(1, s.boosts.calls.size(), "right click does not use");
        assertTrue(alice.items().isEmpty(), "1.4.3: taking a clan bottle out is off");
        assertEquals(2, s.vault.item(alpha.uuid(), id).amount());

        // bottle-withdraw: true turns it back on
        org.mockito.Mockito.when(s.plugin.menus()).thenReturn(
                s.menusWith(dir, "clan-vault", java.util.Map.of("bottle-withdraw", true)));
        s.clan(alice, "kho");
        Thread.sleep(300);
        alice.clickSlot(MenuAccess.slots(alice.menu, 'X').get(0), ClickType.RIGHT);
        assertEquals(Material.HONEY_BOTTLE, alice.items().get(0).getType());
        assertEquals(1, s.vault.item(alpha.uuid(), id).amount());
    }

    @Test
    void tabMarkersFollowTheOpenTab() {
        s.clan(alice, "shop");
        List<Integer> tabs = MenuAccess.slots(alice.menu, 'T');
        List<Integer> marks = MenuAccess.slots(alice.menu, 'U');
        assertEquals(10, tabs.get(0) + 9, "tabs start one slot right");
        assertEquals(tabs.get(0) + 9, marks.get(0), "a marker under each tab");
        assertEquals(Material.LIME_STAINED_GLASS_PANE, MenuAccess.item(alice.menu, marks.get(0)).getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, MenuAccess.item(alice.menu, marks.get(1)).getType());
        alice.clickSlot(tabs.get(2), ClickType.LEFT);
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, MenuAccess.item(alice.menu, marks.get(0)).getType());
        assertEquals(Material.LIME_STAINED_GLASS_PANE, MenuAccess.item(alice.menu, marks.get(2)).getType());
        alice.clickSlot(marks.get(1), ClickType.LEFT);
        assertEquals(Material.LIME_STAINED_GLASS_PANE, MenuAccess.item(alice.menu, marks.get(1)).getType(),
                "a marker opens its tab");
        // finance between the arrows, no storage button
        int m = MenuAccess.slots(alice.menu, 'M').get(0);
        assertEquals(Material.AMETHYST_SHARD, MenuAccess.item(alice.menu, m).getType());
        assertEquals(m - 1, MenuAccess.slots(alice.menu, 'P').get(0));
        assertEquals(m + 1, MenuAccess.slots(alice.menu, 'N').get(0));
        assertTrue(MenuAccess.slots(alice.menu, 'K').isEmpty());

        s.clan(alice, "kho");
        List<Integer> vaultMarks = MenuAccess.slots(alice.menu, 'U');
        assertEquals(Material.LIME_STAINED_GLASS_PANE, MenuAccess.item(alice.menu, vaultMarks.get(0)).getType());
        alice.clickSlot(MenuAccess.slots(alice.menu, 'T').get(1), ClickType.LEFT);
        assertEquals(Material.LIME_STAINED_GLASS_PANE, MenuAccess.item(alice.menu, vaultMarks.get(1)).getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, MenuAccess.item(alice.menu, vaultMarks.get(0)).getType());
        int barrel = MenuAccess.slots(alice.menu, 'M').get(0);
        assertEquals(Material.BARREL, MenuAccess.item(alice.menu, barrel).getType());
        assertEquals(barrel - 1, MenuAccess.slots(alice.menu, 'P').get(0));
        assertEquals(barrel + 1, MenuAccess.slots(alice.menu, 'N').get(0));
        assertTrue(MenuAccess.slots(alice.menu, 'S').isEmpty(), "no shop button in the storage");
        for (int row = 0; row < 6; row++) {
            assertEquals(Material.GRAY_STAINED_GLASS_PANE, MenuAccess.item(alice.menu, row * 9 + 8).getType(),
                    "framed: right column, row " + row);
        }
    }

    // ------------------------------------------------------------------ staff (§4.6, §5.4, §5.6)

    @Test
    void staffEditsTheShopInGame() {
        staff.cursor = item(Material.DIAMOND);
        ClanShopMenu menu = new ClanShopMenu(s.plugin, staff.player, "khac").editing(true);
        menu.open();
        staff.clickSlot(MenuAccess.slots(staff.menu, 'X').get(0), ClickType.LEFT);
        assertEquals(com.dex.boxclans.chat.ChatPrompt.Result.RETRY, s.type(staff, "abc"));
        assertEquals(com.dex.boxclans.chat.ChatPrompt.Result.DONE, s.type(staff, "1.500"));
        ShopEntry added = s.shop.entries("khac", true).get(0);
        assertEquals(1500, added.price());
        assertEquals("DIAMOND", new String(added.item(), java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(staff.got("Đã thêm"), staff.messages.toString());
        // players never get the edit mode: a click on the entry is a purchase, not the editor
        money(5000);
        new ClanShopMenu(s.plugin, alice.player, "khac").editing(true).open();
        alice.clickSlot(MenuAccess.slots(alice.menu, 'X').get(0), ClickType.LEFT);
        assertInstanceOf(ConfirmMenu.class, alice.menu);
    }

    @Test
    void shopCommands() {
        staff.hand = item(Material.TRIPWIRE_HOOK);
        s.admin(staff.player, "shop", "add", "key", "250", "3");
        ShopEntry e = s.shop.entries("key", true).get(0);
        assertEquals(250, e.price());
        assertEquals(3, e.weeklyLimit());
        s.admin(s.console, "shop", "price", String.valueOf(e.id()), "400");
        s.admin(s.console, "shop", "limit", "#" + e.id(), "0");
        s.admin(s.console, "shop", "category", String.valueOf(e.id()), "boost");
        s.admin(s.console, "shop", "addcmd", String.valueOf(e.id()), "crate", "key", "give", "{player}", "vote", "1");
        s.admin(s.console, "shop", "hide", String.valueOf(e.id()));
        ShopEntry after = s.shop.entry(e.id());
        assertEquals(400, after.price());
        assertEquals(0, after.weeklyLimit());
        assertEquals("boost", after.category());
        assertEquals(List.of("crate key give {player} vote 1"), after.commands());
        assertEquals(Delivery.COMMAND, after.delivery());
        assertTrue(after.hidden());
        FakeServer.CONSOLE.clear();
        s.admin(s.console, "shop", "category", String.valueOf(e.id()), "icon");
        assertTrue(FakeServer.CONSOLE.stream().anyMatch(m -> m.contains("Không có danh mục icon")), "icons are not an item category");
        s.admin(s.console, "shop", "remove", String.valueOf(e.id()));
        assertNull(s.shop.entry(e.id()));
    }

    @Test
    void storageAndBoostCommands() throws Exception {
        ShopEntry key = entry(Material.TRIPWIRE_HOOK, "key", 10, 0);
        s.plugin.shopEditor().addCommand(key.id(), "crate key give {player} vote 1");
        s.admin(s.console, "kho", "giveshop", "player", "Bob", String.valueOf(key.id()), "5");
        VaultItem v = s.vault.items(alpha.uuid(), null, x -> x).get(0);
        assertEquals(5, v.amount());
        assertEquals(Delivery.COMMAND, v.delivery());
        assertTrue(alice.got("Kho bang hội nhận được 5 x Tripwire Hook"), alice.messages.toString());
        staff.hand = item(Material.DIAMOND);
        s.admin(staff.player, "kho", "give", "clan", "Alpha", "khac", "2", "-s");
        assertEquals(2, s.vault.items(alpha.uuid(), "khac", x -> x).get(0).amount());
        s.admin(s.console, "kho", "remove", "Alpha", String.valueOf(v.id()), "2");
        assertEquals(3, s.vault.item(alpha.uuid(), v.id()).amount());
        assertEquals(2, history(HistoryType.VAULT_GIVE).size());
        assertEquals(1, history(HistoryType.VAULT_REMOVE).size());

        s.admin(s.console, "boost", "start", "clan", "Alpha", "mining", "2", "1h");
        assertEquals("start mining 20 3600 true null", s.boosts.calls.get(0));
        s.admin(s.console, "boost", "start", "player", "Bob", "quest", "1.5", "30m");
        assertEquals("start quest 15 1800 true null", s.boosts.calls.get(1));
        s.admin(s.console, "boost", "stop", "clan", "Alpha", "mining");
        assertEquals("stop mining", s.boosts.calls.get(2));
        FakeServer.CONSOLE.clear();
        s.admin(s.console, "boost", "start", "clan", "Alpha", "nope", "2", "1h");
        assertTrue(FakeServer.CONSOLE.stream().anyMatch(m -> m.contains("không có loại boost nope")), FakeServer.CONSOLE.toString());
        s.boostsInstalled = false;
        s.admin(s.console, "boost", "stop", "clan", "Alpha", "mining");
        assertEquals(3, s.boosts.calls.size());
    }

    @Test
    void disbandingDeletesTheStorage() throws Exception {
        bottle();
        assertNotNull(s.db.loadVault().stream().filter(r -> r.clan().equals(alpha.uuid())).findFirst().orElse(null));
        s.clan(alice, "disband");
        alice.click('C');
        assertTrue(s.db.loadVault().isEmpty());
    }
}
