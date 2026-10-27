package com.dex.boxclans;

import com.dex.boxclans.FakeServer.FakePlayer;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.chat.ChatPrompt;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.config.MenuConfig;
import com.dex.boxclans.config.Settings;
import com.dex.boxclans.gui.ClanListMenu;
import com.dex.boxclans.gui.ClanMenu;
import com.dex.boxclans.gui.ConfirmMenu;
import com.dex.boxclans.gui.Flag;
import com.dex.boxclans.gui.FlagMakerMenu;
import com.dex.boxclans.gui.InvitesMenu;
import com.dex.boxclans.gui.LeaderboardMenu;
import com.dex.boxclans.gui.MenuAccess;
import com.dex.boxclans.gui.PreviewMenu;
import com.dex.boxclans.gui.ScoreLore;
import com.dex.boxclans.gui.SettingsMenu;
import com.dex.boxclans.points.KillEventManager;
import com.dex.boxclans.text.Text;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** P7: the 8 menus of §8.4 on the fake server. */
class GuiTest {

    @TempDir
    File dir;
    FakeServer s;
    FakePlayer a;
    FakePlayer b;
    FakePlayer c;
    ClanData alpha;

    @BeforeEach
    void setUp() {
        s = new FakeServer(dir);
        a = s.join("Alice");
        b = s.join("Bob");
        c = s.join("Carl");
        s.clan(a, "create");
        s.type(a, "&c&lRồng &6Đỏ");
        a.click('C');
        alpha = s.manager.of(a.id);
        s.clan(a, "invite", "Bob");
        s.clan(b, "accept");
    }

    @AfterEach
    void tearDown() {
        s.close();
    }

    private int slotOf(FakePlayer p, char ch) {
        return MenuAccess.slots(p.menu, ch).get(0);
    }

    private ItemStack item(FakePlayer p, int slot) {
        return MenuAccess.item(p.menu, slot);
    }

    @Test
    void mainMenuMembersAndLeaderClicks() {
        s.clan(a);
        assertInstanceOf(ClanMenu.class, a.menu);
        assertEquals("Thành viên | Rồng Đỏ", FakeServer.TITLES.get(a.menu));
        // sorted by role: Alice (Minh Chủ) then Bob
        assertEquals(Material.PLAYER_HEAD, item(a, 0).getType());
        assertEquals(Material.PLAYER_HEAD, item(a, 1).getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, item(a, 2).getType(), "invite slots");
        assertEquals(Material.WHITE_BANNER, item(a, slotOf(a, 'I')).getType(), "no flag yet: default icon");

        a.clickSlot(1, ClickType.LEFT);
        assertEquals(ClanRole.VICE, alpha.roleOf(b.id), "left = Phó Bang");
        a.clickSlot(1, ClickType.RIGHT);
        assertEquals(ClanRole.MEMBER, alpha.roleOf(b.id), "right = Thành Viên");
        a.clickSlot(0, ClickType.LEFT);
        assertEquals(ClanRole.LEADER, alpha.roleOf(a.id), "clicking yourself does nothing");

        a.clickSlot(1, ClickType.SHIFT_LEFT);
        assertInstanceOf(ConfirmMenu.class, a.menu, "transfer asks first");
        a.click('D');
        assertEquals(a.id, alpha.leader());

        s.clan(a);
        a.clickSlot(1, ClickType.SHIFT_RIGHT);
        assertInstanceOf(ConfirmMenu.class, a.menu, "kick asks first");
        a.click('C');
        assertFalse(alpha.isMember(b.id));

        // empty slot: invite prompt
        s.clan(a);
        a.clickSlot(2, ClickType.LEFT);
        assertTrue(s.prompts.isOpen(a.id));
        assertEquals(ChatPrompt.Result.DONE, s.type(a, "Carl"));
        assertTrue(c.got("mời bạn vào bang hội Rồng Đỏ"));

        // pending view: click to cancel the invite (the bundled layout has no F since 1.4.4: put it back here)
        when(s.plugin.menus()).thenReturn(s.menusWith(dir, "clan-detail", Map.of("layout", List.of(
                "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "HKAFI.GSL"))));
        s.clan(a);
        a.click('F');
        assertEquals("Lời mời đang chờ | Rồng Đỏ", FakeServer.TITLES.get(a.menu));
        a.clickSlot(0, ClickType.LEFT);
        assertFalse(s.manager.invites().has(alpha.uuid(), c.id));

        s.clan(a);
        a.click('S');
        a.click('L');
        assertInstanceOf(ClanListMenu.class, a.menu);
        s.clan(a);
        a.click('A');
        assertInstanceOf(SettingsMenu.class, a.menu);
    }

    @Test
    void memberWithoutRightsCannotActOnHeads() {
        s.clan(b);
        a.clear();
        b.clickSlot(0, ClickType.SHIFT_RIGHT);
        assertTrue(alpha.isMember(a.id));
        b.clickSlot(0, ClickType.LEFT);
        assertEquals(ClanRole.LEADER, alpha.roleOf(a.id));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, item(b, 2).getType(), "filler, no invite button");
        b.clickSlot(2, ClickType.LEFT);
        assertFalse(s.prompts.isOpen(b.id));
    }

    @Test
    void listPreviewAndJoinByInvite() {
        s.clan(c);
        assertInstanceOf(ClanListMenu.class, c.menu, "no clan: /clan opens the list");
        assertEquals("Danh sách bang hội", FakeServer.TITLES.get(c.menu));
        c.clickSlot(0, ClickType.LEFT);
        assertInstanceOf(PreviewMenu.class, c.menu);
        assertEquals("Xem bang hội | Rồng Đỏ", FakeServer.TITLES.get(c.menu));
        c.click('J');
        assertNull(s.manager.of(c.id), "not invited: invite only");
        c.click('B');
        assertInstanceOf(ClanListMenu.class, c.menu);

        s.clan(a, "invite", "Carl");
        c.clickSlot(0, ClickType.LEFT);
        c.click('J');
        assertEquals(alpha, s.manager.of(c.id));
        assertInstanceOf(ClanMenu.class, c.menu);

        // in a clan: A = your clan
        s.clan(c, "list");
        c.click('A');
        assertInstanceOf(ClanMenu.class, c.menu);
        // without a clan: A = create
        FakePlayer d = s.join("Dan");
        s.clan(d, "list");
        d.click('A');
        assertTrue(s.prompts.isOpen(d.id));
        s.type(d, "cancel");
        s.clan(d, "list");
        d.click('L');
        assertInstanceOf(LeaderboardMenu.class, d.menu);
    }

    @Test
    void invitesMenuAcceptAndDeny() {
        FakePlayer d = s.join("Dan");
        s.clan(d, "create");
        s.type(d, "Hắc Long");
        d.click('C');
        s.clan(a, "invite", "Carl");
        s.clan(d, "invite", "Carl");
        s.clan(c, "invites");
        assertInstanceOf(InvitesMenu.class, c.menu);
        c.clickSlot(0, ClickType.RIGHT);
        assertEquals(1, s.manager.invites().forPlayer(c.id).size(), "right-click declined one");
        c.clickSlot(0, ClickType.LEFT);
        assertNotNull(s.manager.of(c.id));
        assertInstanceOf(ClanMenu.class, c.menu);
    }

    @Test
    void leaderboardOpensPreviews() {
        alpha.points(40);
        s.manager.publish(alpha);
        s.clan(c, "top");
        assertInstanceOf(LeaderboardMenu.class, c.menu);
        assertEquals("Bảng xếp hạng bang hội", FakeServer.TITLES.get(c.menu));
        int first = MenuAccess.slots(c.menu, 'X').get(0);
        c.clickSlot(first, ClickType.LEFT);
        assertInstanceOf(PreviewMenu.class, c.menu);
        c.click('B');
        assertInstanceOf(LeaderboardMenu.class, c.menu, "back returns to the leaderboard");
    }

    @Test
    void settingsToggles() {
        s.clan(a, "settings");
        assertInstanceOf(SettingsMenu.class, a.menu);
        a.click('V');
        assertTrue(alpha.friendlyFire());
        a.click('C');
        assertTrue(s.plugin.playerSettings().get(a.id).chatMode());
        a.click('S');
        assertFalse(s.plugin.playerSettings().get(a.id).chatSound());
        a.click('M');
        assertFalse(s.plugin.playerSettings().get(a.id).memberSound());
        a.click('R');
        assertTrue(s.prompts.isOpen(a.id), "rename prompt");
        s.type(a, "cancel");
        s.clan(a, "settings");
        a.click('F');
        assertInstanceOf(FlagMakerMenu.class, a.menu);
        s.clan(a, "settings");
        a.click('D');
        assertInstanceOf(ConfirmMenu.class, a.menu);
        a.click('D');
        s.clan(a, "settings");
        a.click('B');
        assertInstanceOf(ClanMenu.class, a.menu);

        s.clan(b, "settings");
        b.click('R');
        assertFalse(s.prompts.isOpen(b.id));
        assertTrue(b.got("Vai trò của bạn không có quyền"));
        b.click('L');
        assertInstanceOf(ConfirmMenu.class, b.menu);
        b.click('C');
        assertFalse(alpha.isMember(b.id));
    }

    @Test
    void flagMakerBuildsATextFlag() {
        s.clan(a, "flag");
        FlagMakerMenu menu = (FlagMakerMenu) a.menu;
        assertNull(MenuAccess.draft(menu));
        a.clickSlot(DyeColor.RED.ordinal(), ClickType.LEFT);                // base colour
        assertEquals(DyeColor.RED, MenuAccess.draft(menu).base());
        a.clickSlot(46, ClickType.LEFT);                                    // change pattern colour
        a.clickSlot(DyeColor.YELLOW.ordinal(), ClickType.LEFT);
        a.clickSlot(4, ClickType.LEFT);                                     // 5th pattern of the list: cross
        assertEquals("RED|YELLOW:cross", MenuAccess.draft(menu).encode());
        for (int i = 0; i < 5; i++) a.clickSlot(0, ClickType.LEFT);
        assertEquals(6, MenuAccess.draft(menu).layers().size());
        a.clickSlot(0, ClickType.LEFT);
        assertEquals(6, MenuAccess.draft(menu).layers().size(), "6 layers without a banner permission");
        assertTrue(a.got("Cờ chỉ được tối đa 6 lớp hoa văn"));
        a.clickSlot(51, ClickType.LEFT);                                    // undo
        assertEquals(5, MenuAccess.draft(menu).layers().size());
        a.clickSlot(53, ClickType.LEFT);                                    // save
        assertTrue(alpha.flag().startsWith("RED|YELLOW:cross|YELLOW:border"), alpha.flag());
        assertEquals(alpha.flag(), s.index.get(alpha.uuid()).flag());
        assertInstanceOf(SettingsMenu.class, a.menu);
        assertTrue(a.got("Đã lưu cờ bang hội"));
        assertEquals(5, Flag.parse(alpha.flag()).layers().size());

        // reset + save clears; a member without EDIT_FLAG cannot open it
        s.clan(a, "flag");
        a.clickSlot(52, ClickType.LEFT);
        a.clickSlot(53, ClickType.LEFT);
        assertEquals("", alpha.flag());
        s.clan(b, "flag");
        assertFalse(b.menu instanceof FlagMakerMenu);
    }

    @Test
    void bannerPermissionRaisesTheLayerLimit() {
        a.perms.add("boxclans.banner.12");
        s.clan(a, "flag");
        FlagMakerMenu menu = (FlagMakerMenu) a.menu;
        a.clickSlot(0, ClickType.LEFT);
        for (int i = 0; i < 13; i++) a.clickSlot(0, ClickType.LEFT);
        assertEquals(12, MenuAccess.draft(menu).layers().size());
    }

    @Test
    void scoreLoreFromPointsYml() {
        // 230 kill points today, as in the §8.4 example
        for (int i = 0; i < 23; i++) {
            s.kills.handle(new com.dex.boxclans.points.KillProcessor.Kill(a.id, "1.1.1." + i, false,
                    s.join("V" + i).id, "2.2.2." + i, false));
        }
        assertEquals(230, s.points.clanToday(alpha.uuid(), "kill"));
        Settings.AntiFarm af = s.settings.antiFarm();
        ScoreLore.Lines lines = ScoreLore.build(s.plugin.menus().menu("settings").getConfigurationSection("score"),
                s.points.config(), null, af, "10 phút", alpha.uuid(), s.points::clanToday);
        List<String> text = lines.sources().stream().map(m -> Text.plain(Text.parse(m.value()))).toList();
        assertEquals(List.of(
                "- Hạ gục người chơi +10 Danh Vọng",
                "   Tối đa 500 mỗi ngày cho cả bang",
                "   (reset 0h) Hôm nay: 230/500",
                "- Bị hạ gục không bị trừ Danh Vọng",
                "- KOTH top 1 / 2 / 3 +50 / +30 / +15",
                "- Thắng Clan War +100 Danh Vọng",
                "- Hoàn thành nhiệm vụ tuỳ nhiệm vụ",
                "- Đánh boss tuỳ boss"), text);
        assertEquals(List.of("(!) Giết cùng một người quá 3 lần", "(!) trong 10 phút sẽ không được tính!"),
                lines.warnings().stream().map(m -> Text.plain(Text.parse(m.value()))).toList());
        assertTrue(lines.killEvent().isEmpty());

        s.killEvents.start("loan-chien", Duration.ofMinutes(10));
        KillEventManager.Active active = s.killEvents.current();
        ScoreLore.Lines during = ScoreLore.build(s.plugin.menus().menu("settings").getConfigurationSection("score"),
                s.points.config(), active, af, "10 phút", alpha.uuid(), s.points::clanToday);
        assertEquals(List.of("", "Đang diễn ra: LOẠN CHIẾN", "Hạ gục +10 / bị hạ gục -5", "(không giới hạn)"),
                during.killEvent().stream().map(m -> Text.plain(Text.parse(m.value()))).toList());
    }

    @Test
    void editedYamlChangesTheMenuAfterReload() {
        s.clan(a, "settings");
        assertEquals("Cài đặt bang hội", FakeServer.TITLES.get(a.menu));
        int pvpSlot = MenuAccess.slots(a.menu, 'V').get(0);
        assertEquals(5, pvpSlot, "1.4.3: toggles on slots 6-9 of the first row");
        assertEquals(Material.TURTLE_HELMET, item(a, pvpSlot).getType());

        MenuConfig edited = s.menusWith(dir, "settings", Map.of(
                "title", '&' + "cCÀI ĐẶT",
                "layout", List.of("V.F.R.C.S", "M........", "OZ..B..LD"),
                "items.V.material", "DIAMOND_SWORD"));
        when(s.plugin.menus()).thenReturn(edited);
        s.clan(a, "settings");
        assertEquals("CÀI ĐẶT", FakeServer.TITLES.get(a.menu));
        assertEquals(0, MenuAccess.slots(a.menu, 'V').get(0));
        assertEquals(Material.DIAMOND_SWORD, item(a, 0).getType());
        a.click('V');
        assertTrue(alpha.friendlyFire(), "the button moved and still works");
    }
}
