package com.dex.boxclans;

import com.dex.boxclans.FakeServer.FakePlayer;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.chat.ChatPrompt;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.gui.ConfirmMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the §9 commands on a fake server (P4 "Xong khi"). */
class CommandsTest {

    @TempDir
    File dir;
    FakeServer s;

    @BeforeEach
    void setUp() {
        s = new FakeServer(dir);
    }

    @AfterEach
    void tearDown() {
        s.close();
    }

    /** Creates a clan through the prompt and the confirmation screen. */
    private ClanData create(FakePlayer p, String name) {
        s.clan(p, "create");
        assertTrue(s.prompts.isOpen(p.id));
        assertEquals(ChatPrompt.Result.DONE, s.type(p, name));
        assertInstanceOf(ConfirmMenu.class, p.menu);
        p.click('C');
        ClanData clan = s.manager.of(p.id);
        assertNotNull(clan);
        return clan;
    }

    private void joinClan(FakePlayer leader, FakePlayer p) {
        s.clan(leader, "invite", p.name);
        s.clan(p, "accept");
        assertEquals(s.manager.of(leader.id), s.manager.of(p.id));
    }

    @Test
    void createThroughPromptAndConfirm() {
        FakePlayer a = s.join("Alice");
        FakePlayer other = s.join("Bob");
        s.clan(a, "create");
        assertTrue(a.got("Nhập tên bang hội"), a.messages.toString());
        // wrong input: error, prompt still open
        assertEquals(ChatPrompt.Result.RETRY, s.type(a, "ab"));
        assertTrue(a.got("Tên quá ngắn"));
        assertEquals(ChatPrompt.Result.RETRY, s.type(a, "&kRồng"));
        assertTrue(a.got("Không được dùng"));
        assertTrue(s.prompts.isOpen(a.id));
        // valid: confirmation screen with the preview
        assertEquals(ChatPrompt.Result.DONE, s.type(a, "&c&lRồng &6Đỏ"));
        assertInstanceOf(ConfirmMenu.class, a.menu);
        assertNull(s.manager.of(a.id), "nothing created before the confirmation");
        a.click('C');
        ClanData clan = s.manager.of(a.id);
        assertEquals("RongDo", clan.key());
        assertTrue(a.got("Bạn đã tạo bang hội Rồng Đỏ! Mã bang hội: RongDo"), a.messages.toString());
        assertTrue(other.got("Alice vừa lập bang hội Rồng Đỏ"));

        // same key, other colours
        s.clan(other, "create");
        assertEquals(ChatPrompt.Result.RETRY, s.type(other, "&aRong Do"));
        assertTrue(other.got("Đã có bang hội dùng mã RongDo"));
        assertEquals(ChatPrompt.Result.CANCELLED, s.type(other, "cancel"));
        assertTrue(other.got("Đã huỷ nhập"));
    }

    /** A chat event whose message another chat plugin already stripped of its & codes. */
    private static io.papermc.paper.event.player.AsyncChatEvent strippedChat(FakePlayer p, String typed,
                                                                            boolean signed) {
        net.kyori.adventure.chat.SignedMessage sm = null;
        if (signed) {
            sm = org.mockito.Mockito.mock(net.kyori.adventure.chat.SignedMessage.class);
            org.mockito.Mockito.when(sm.message()).thenReturn(typed);
        }
        String stripped = typed.replaceAll("&[0-9a-fk-or]", "");
        return new io.papermc.paper.event.player.AsyncChatEvent(true, p.player, new java.util.HashSet<>(),
                io.papermc.paper.chat.ChatRenderer.defaultRenderer(), Component.text(stripped),
                signed ? Component.text(stripped) : Component.text(typed), sm);
    }

    @Test
    void promptKeepsColourCodesEvenWhenAChatPluginStripsThem() {
        com.dex.boxclans.chat.PromptListener listener = new com.dex.boxclans.chat.PromptListener(s.plugin, s.prompts);
        FakePlayer a = s.join("Alice");
        s.clan(a, "create");
        var event = strippedChat(a, "&dTheHmoies", true);
        listener.onChat(event);
        assertTrue(event.isCancelled());
        a.click('C');
        ClanData clan = s.manager.of(a.id);
        assertEquals("&dTheHmoies", clan.name().raw());
        assertEquals("§dTheHmoies", clan.name().legacy(), "pink, not white");
        assertEquals("§dTheHmoies", s.index.of(a.id).name());

        // unsigned chat: the original component is used
        FakePlayer b = s.join("Bob");
        s.clan(b, "create");
        listener.onChat(strippedChat(b, "&x&F&F&4&1&2&7Hac Long", false));
        b.click('C');
        assertTrue(s.manager.of(b.id).name().legacy().startsWith("§x§f§f§4§1§2§7"), s.manager.of(b.id).name().legacy());
    }

    @Test
    void cancelOnTheConfirmationScreen() {
        FakePlayer a = s.join("Alice");
        s.clan(a, "create");
        s.type(a, "Hắc Long");
        a.click('D');
        assertNull(s.manager.of(a.id));
        assertTrue(a.got("Đã huỷ"));
    }

    @Test
    void inviteAcceptDenyUninvite() {
        FakePlayer a = s.join("Alice");
        FakePlayer b = s.join("Bob");
        FakePlayer c = s.join("Carl");
        ClanData clan = create(a, "Alpha");

        s.clan(a, "invite", "Bob");
        assertTrue(a.got("Đã mời Bob"));
        assertTrue(b.got("Alice mời bạn vào bang hội Alpha"));
        s.clan(b, "invites");
        assertInstanceOf(com.dex.boxclans.gui.InvitesMenu.class, b.menu);
        s.clan(b, "accept");
        assertTrue(b.got("Bạn đã gia nhập bang hội Alpha"));
        assertTrue(a.got("Bob đã gia nhập bang hội"));
        assertEquals(ClanRole.MEMBER, clan.roleOf(b.id));

        // Thành Viên cannot invite
        s.clan(b, "invite", "Carl");
        assertTrue(b.got("Vai trò của bạn không có quyền"));

        // invite through the prompt, then deny
        s.clan(a, "invite");
        assertTrue(s.prompts.isOpen(a.id));
        assertEquals(ChatPrompt.Result.RETRY, s.type(a, "Nobody"));
        assertTrue(a.got("Người chơi Nobody không online"));
        assertEquals(ChatPrompt.Result.DONE, s.type(a, "carl"));
        assertTrue(c.got("mời bạn vào bang hội Alpha"));
        s.clan(c, "deny", "alpha");
        assertTrue(c.got("Bạn đã từ chối lời mời của Alpha"));
        assertTrue(a.got("Carl đã từ chối lời mời"));

        s.clan(a, "invite", "Carl");
        s.clan(a, "uninvite", "Carl");
        assertTrue(a.got("Đã huỷ lời mời của Carl"));
        s.clan(c, "accept", "Alpha");
        assertTrue(c.got("Bạn không có lời mời nào từ bang hội này"));
        s.clan(a, "invite", "Ghost");
        assertTrue(a.got("không online"));
    }

    @Test
    void rolesThroughCommandsAndConfirmations() {
        FakePlayer a = s.join("Alice");
        FakePlayer b = s.join("Bob");
        FakePlayer c = s.join("Carl");
        ClanData clan = create(a, "Alpha");
        joinClan(a, b);
        joinClan(a, c);

        s.clan(a, "promote", "Bob");
        assertEquals(ClanRole.VICE, clan.roleOf(b.id));
        assertTrue(c.got("Bob giờ là Phó Bang"));

        // second Phó Bang: swap screen
        s.clan(a, "setrole", "Carl", "PhoBang");
        assertInstanceOf(ConfirmMenu.class, a.menu);
        a.click('C');
        assertEquals(ClanRole.VICE, clan.roleOf(c.id));
        assertEquals(ClanRole.MEMBER, clan.roleOf(b.id));

        s.clan(a, "demote", "Carl");
        assertEquals(ClanRole.MEMBER, clan.roleOf(c.id));
        s.clan(b, "promote", "Carl");
        assertTrue(b.got("Chỉ Minh Chủ"));
        s.clan(a, "setrole", "Carl", "Boss");
        assertTrue(a.got("Cách dùng"));

        // transfer
        s.clan(a, "transfer", "Bob");
        assertInstanceOf(ConfirmMenu.class, a.menu);
        a.click('C');
        assertEquals(b.id, clan.leader());
        assertEquals(ClanRole.MEMBER, clan.roleOf(a.id));
        assertTrue(c.got("Bob là Minh Chủ mới"), c.messages.toString());
        s.clan(a, "leave");
        a.click('C');
        assertFalse(clan.isMember(a.id));
        assertTrue(b.got("Alice đã rời bang hội"));
    }

    @Test
    void kickLeaveDisband() {
        FakePlayer a = s.join("Alice");
        FakePlayer b = s.join("Bob");
        FakePlayer c = s.join("Carl");
        ClanData clan = create(a, "Alpha");
        joinClan(a, b);
        joinClan(a, c);

        s.clan(a, "leave");
        assertTrue(a.got("Minh Chủ không thể rời bang"));
        s.clan(b, "kick", "Carl");
        assertTrue(b.got("Vai trò của bạn không có quyền"));
        s.clan(a, "kick", "Carl");
        assertFalse(clan.isMember(c.id));
        assertTrue(c.got("Bạn đã bị kick khỏi bang hội Alpha"));
        assertTrue(b.got("Carl đã bị Alice kick"));

        s.clan(b, "disband");
        assertTrue(b.got("Chỉ Minh Chủ"));
        s.clan(a, "disband");
        a.click('C');
        assertNull(s.manager.of(a.id));
        assertTrue(a.got("Bạn đã giải tán bang hội Alpha"));
        assertTrue(b.got("Bang hội Alpha của bạn đã bị giải tán"));
        assertNull(s.index.byKey("Alpha"));
    }

    @Test
    void renamePvpDescriptionInfoListTopHelp() {
        FakePlayer a = s.join("Alice");
        FakePlayer b = s.join("Bob");
        ClanData clan = create(a, "Alpha");
        joinClan(a, b);

        s.clan(a, "rename");
        assertEquals(ChatPrompt.Result.RETRY, s.type(a, "Alpha"));
        assertTrue(a.got("Tên mới giống hệt"));
        assertEquals(ChatPrompt.Result.DONE, s.type(a, "&x&F&F&0&0&0&0Alpha"));
        assertTrue(b.got("Bang hội đã đổi tên"));
        assertEquals(ChatPrompt.Result.NONE, s.type(a, "x"));
        s.clan(a, "rename");
        assertEquals(ChatPrompt.Result.DONE, s.type(a, "Beta Team"));
        assertEquals("BetaTeam", clan.key());
        s.clan(a, "rename");
        assertEquals(ChatPrompt.Result.RETRY, s.type(a, "Gamma"));
        assertTrue(a.got("Bạn chỉ được đổi chữ của tên sau 30 phút"), a.messages.toString());
        s.type(a, "cancel");
        s.clan(b, "rename");
        assertTrue(b.got("Vai trò của bạn không có quyền"));

        s.clan(a, "pvp");
        assertTrue(clan.friendlyFire());
        assertTrue(b.got("Alice đã BẬT PvP trong bang hội"));
        s.clan(b, "pvp");
        assertTrue(b.got("Vai trò của bạn không có quyền"));

        s.clan(a, "description", "Bang", "mạnh", "&cnhất");
        assertEquals("Bang mạnh nhất", clan.description(), "colour codes are removed");
        s.clan(a, "description", "x".repeat(51));
        assertTrue(a.got("Mô tả tối đa 50"), a.messages.toString());

        a.clear();
        s.clan(a, "info");
        assertTrue(a.got("Beta Team (mã bang hội BetaTeam)"), a.messages.toString());
        assertTrue(a.got("Minh Chủ: Alice"));
        assertTrue(a.got("Thành viên: 2/8"));
        assertTrue(a.got("Mô tả: Bang mạnh nhất"));
        // a player gets the preview menu, the console gets the chat lines
        s.clan(a, "info", "betateam");
        assertInstanceOf(com.dex.boxclans.gui.PreviewMenu.class, a.menu);
        FakeServer.CONSOLE.clear();
        s.clan.onCommand(s.console, s.command, "clan", new String[]{"info", "Bob"});
        assertTrue(FakeServer.CONSOLE.contains(" Beta Team (mã bang hội BetaTeam)"), FakeServer.CONSOLE.toString());
        FakeServer.CONSOLE.clear();
        s.clan.onCommand(s.console, s.command, "clan", new String[]{"info", "\"Beta", "Team\""});
        assertTrue(FakeServer.CONSOLE.contains(" Beta Team (mã bang hội BetaTeam)"));
        s.clan.onCommand(s.console, s.command, "clan", new String[]{"list"});
        assertTrue(FakeServer.CONSOLE.contains(" 1. Beta Team 2/8 thành viên, 0 Danh Vọng"), FakeServer.CONSOLE.toString());
        s.clan.onCommand(s.console, s.command, "clan", new String[]{"top"});
        assertTrue(FakeServer.CONSOLE.stream().anyMatch(m -> m.startsWith(" #1 Beta Team - 0 Danh Vọng")), FakeServer.CONSOLE.toString());
        s.clan(a, "list");
        assertInstanceOf(com.dex.boxclans.gui.ClanListMenu.class, a.menu);
        s.clan(a, "top");
        assertInstanceOf(com.dex.boxclans.gui.LeaderboardMenu.class, a.menu);
        s.clan(a, "help");
        assertTrue(a.got("/clan create"));
        s.clan(a, "nonsense");
        assertTrue(a.got("Lệnh không tồn tại"));
        s.clan.onCommand(s.console, s.command, "clan", new String[]{"info", "BetaTeam"});
        s.clan.onCommand(s.console, s.command, "clan", new String[]{"create"});
    }

    @Test
    void clanChatUsesRoleColoursAndIgnoresTypedCodes() {
        FakePlayer a = s.join("Alice");
        FakePlayer b = s.join("Bob");
        FakePlayer spy = s.join("Staff", "boxclans.spy");
        FakePlayer outsider = s.join("Eve");
        create(a, "Alpha");
        joinClan(a, b);
        s.admin(spy.player, "spy");
        assertTrue(spy.got("Đã bật xem chat"));

        a.clear();
        b.clear();
        s.cc(a, "chào", "&canh", "<red>em");
        String expected = "Bang Hội | (Minh Chủ) Alice: chào &canh <red>em";
        assertEquals(expected, b.last());
        assertEquals(expected, a.last());
        assertFalse(outsider.got("chào"));
        assertTrue(spy.last().startsWith("Giám sát | Alpha (Minh Chủ) Alice"), spy.last());

        Component line = b.components.get(b.components.size() - 1);
        List<TextColor> colors = new ArrayList<>();
        collect(line, "Alice", colors);
        assertEquals(List.of(NamedTextColor.RED), colors, "sender name in the Minh Chủ colour");
        colors.clear();
        collect(line, "chào &canh <red>em", colors);
        assertEquals(List.of(NamedTextColor.YELLOW), colors, "message always &e, typed codes have no effect");

        s.cc(b, "ok");
        line = a.components.get(a.components.size() - 1);
        colors.clear();
        collect(line, "(Thành Viên) Bob", colors);
        assertEquals(List.of(NamedTextColor.GREEN), colors);

        // chat mode toggle
        s.cc(a);
        assertTrue(a.got("Đã bật chế độ chat bang hội"));
        assertTrue(s.plugin.playerSettings().get(a.id).chatMode());
        s.clan(a, "chat");
        assertFalse(s.plugin.playerSettings().get(a.id).chatMode());
        s.cc(outsider, "hi");
        assertTrue(outsider.got("Bạn chưa có bang hội"));
    }

    private static io.papermc.paper.event.player.AsyncChatEvent chat(FakePlayer p, String text) {
        return new io.papermc.paper.event.player.AsyncChatEvent(true, p.player, new java.util.HashSet<>(),
                io.papermc.paper.chat.ChatRenderer.defaultRenderer(), Component.text(text), Component.text(text), null);
    }

    @Test
    void shortcutAndChatModeFromPublicChat() {
        FakePlayer a = s.join("Alice");
        FakePlayer b = s.join("Bob");
        FakePlayer loner = s.join("Eve");
        create(a, "Alpha");
        joinClan(a, b);
        com.dex.boxclans.chat.ChatListener listener = new com.dex.boxclans.chat.ChatListener(s.plugin);

        var e = chat(a, "!tối nay đi KOTH");
        listener.onChat(e);
        assertTrue(e.isCancelled(), "never reaches public chat");
        assertEquals("Bang Hội | (Minh Chủ) Alice: tối nay đi KOTH", b.last());

        var normal = chat(a, "hello world");
        listener.onChat(normal);
        assertFalse(normal.isCancelled());
        var bang = chat(a, "!");
        listener.onChat(bang);
        assertFalse(bang.isCancelled(), "a lone ! is normal chat");
        var outsider = chat(loner, "!hi");
        listener.onChat(outsider);
        assertFalse(outsider.isCancelled(), "no clan: normal chat");

        s.cc(b);
        var mode = chat(b, "đang ở mine");
        listener.onChat(mode);
        assertTrue(mode.isCancelled());
        assertEquals("Bang Hội | (Thành Viên) Bob: đang ở mine", a.last());
    }

    /** Colours of the text components whose content contains {@code text}. */
    private static void collect(Component c, String text, List<TextColor> out) {
        flatten(c, null, text, out);
    }

    private static void flatten(Component c, TextColor inherited, String text, List<TextColor> out) {
        TextColor color = c.color() != null ? c.color() : inherited;
        if (c instanceof TextComponent t && !t.content().isEmpty() && t.content().contains(text)) out.add(color);
        for (Component child : c.children()) flatten(child, color, text, out);
    }

    @Test
    void adminCommandsFromTheConsole() {
        FakePlayer a = s.join("Alice", "boxclans.members.mvp");
        FakePlayer b = s.join("Bob");
        FakePlayer c = s.join("Carl");
        ClanData clan = create(a, "Alpha");
        joinClan(a, b);
        joinClan(a, c);

        s.admin(s.console, "setrole", "Bob", "PhoBang");
        assertEquals(ClanRole.VICE, clan.roleOf(b.id));
        s.admin(s.console, "setrole", "Carl", "PhoBang");
        assertTrue(FakeServer.CONSOLE.stream().anyMatch(m -> m.contains("gõ lại lệnh kèm confirm") || m.contains("Gõ lại lệnh kèm confirm")),
                FakeServer.CONSOLE.toString());
        s.admin(s.console, "setrole", "Carl", "PhoBang", "confirm");
        assertEquals(ClanRole.VICE, clan.roleOf(c.id));

        s.admin(s.console, "setrole", "Carl", "MinhChu");
        assertEquals(c.id, clan.leader(), "no excess: no confirmation needed");
        assertEquals(ClanRole.MEMBER, clan.roleOf(a.id));

        s.admin(s.console, "rename", "alpha", "&bAlpha", "Prime");
        assertEquals("AlphaPrime", clan.key());
        s.admin(s.console, "kick", "Bob");
        assertFalse(clan.isMember(b.id));
        s.admin(s.console, "kick", "Carl");
        assertTrue(clan.isMember(c.id), "the Minh Chủ cannot be kicked");

        // staff lowered the limit while the leader is offline (an online leader's limit is re-read)
        s.quit(c);
        clan.maxMembers(1);
        s.admin(s.console, "trim", "AlphaPrime");
        assertEquals(1, clan.size());
        assertTrue(clan.isMember(c.id));

        s.admin(s.console, "disband", "AlphaPrime");
        assertNull(s.manager.of(c.id));
        s.admin(s.console, "disband", "nope");
        assertTrue(FakeServer.CONSOLE.stream().anyMatch(m -> m.contains("Không tìm thấy bang hội nope")));
        s.admin(a.player, "disband", "x");
        assertTrue(a.got("Bạn không có quyền"));
        s.admin(s.console);
        assertTrue(FakeServer.CONSOLE.stream().anyMatch(m -> m.contains("Quản trị bang hội")));
    }

    @Test
    void pointsCommandsFromTheConsole() throws Exception {
        FakePlayer a = s.join("Alice");
        FakePlayer b = s.join("Bob");
        ClanData clan = create(a, "&c&lRồng &6Đỏ");
        joinClan(a, b);
        List<String> console = FakeServer.CONSOLE;

        // BoxQuest style: clanadmin points give {player} quest 5 -s
        s.admin(s.console, "points", "give", "Bob", "quest", "5", "-s");
        assertEquals(5, clan.points());
        assertEquals(5, clan.member(b.id).contribution());
        assertFalse(b.got("+5 Danh Vọng"), "-s: members are not told");
        assertTrue(console.stream().anyMatch(m -> m.contains("Đã cộng 5 Danh Vọng (Hoàn thành nhiệm vụ) cho Rồng Đỏ qua Bob")),
                console.toString());

        s.admin(s.console, "points", "give", "Bob", "koth-top1");
        assertEquals(55, clan.points());
        assertTrue(a.got("+50 Danh Vọng (KOTH top 1) từ Bob"), a.messages.toString());

        FakePlayer loner = s.join("Eve");
        s.admin(s.console, "points", "give", "Eve", "quest", "5");
        assertTrue(console.stream().anyMatch(m -> m.contains("người chơi không có bang hội")));
        s.admin(s.console, "points", "give", "Bob", "nope");
        assertTrue(console.stream().anyMatch(m -> m.contains("nguồn không có trong points.yml")));
        assertFalse(loner.got("Danh Vọng"));

        s.admin(s.console, "points", "add", "RongDo", "100", "clan-war-win");
        assertEquals(155, clan.points());
        assertTrue(a.got("+100 Danh Vọng (Thắng Clan War)"));
        s.admin(s.console, "points", "take", "\"Rồng", "Đỏ\"", "30", "-s");
        s.admin(s.console, "points", "take", "rongdo", "30", "-s");
        assertEquals(125, clan.points());
        s.admin(s.console, "points", "set", "RongDo", "77");
        assertEquals(77, clan.points());
        s.admin(s.console, "points", "add", "RongDo", "abc");
        assertTrue(console.stream().anyMatch(m -> m.contains("abc không phải số")));

        s.admin(s.console, "points", "reset", "all");
        assertEquals(77, clan.points(), "reset all needs confirm");
        s.admin(s.console, "points", "reset", "all", "confirm");
        assertEquals(0, clan.points());

        s.admin(s.console, "points", "log", "RongDo");
        long deadline = System.currentTimeMillis() + 5000;
        // the log is sent from the DB thread: wait for the last entry, not only the header
        while (console.stream().noneMatch(m -> m.contains("Hoàn thành nhiệm vụ Bob +5 bởi console"))
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(console.stream().anyMatch(m -> m.contains("Sổ Danh Vọng của Rồng Đỏ (trang 1/1, 6 dòng)")), console.toString());
        assertTrue(console.stream().anyMatch(m -> m.contains("Hoàn thành nhiệm vụ Bob +5 bởi console")), console.toString());

        List<String> sources = s.admin.onTabComplete(s.console, s.command, "clanadmin",
                new String[]{"points", "give", "Bob", "ko"});
        assertEquals(List.of("koth-top1", "koth-top2", "koth-top3"), sources.stream().sorted().toList());
    }

    @Test
    void tabCompleteSuggestsKeysNeverColourCodes() {
        FakePlayer a = s.join("Alice", "boxclans.admin");
        create(a, "&c&lRồng &6Đỏ");
        List<String> keys = s.admin.onTabComplete(a.player, s.command, "clanadmin", new String[]{"disband", ""});
        assertEquals(List.of("RongDo"), keys);
        List<String> roles = s.clan.onTabComplete(a.player, s.command, "clan", new String[]{"setrole", "x", ""});
        assertEquals(List.of("MinhChu", "PhoBang", "ThanhVien"), roles);
        assertTrue(s.clan.onTabComplete(a.player, s.command, "clan", new String[]{"pr"}).contains("promote"));
    }
}
