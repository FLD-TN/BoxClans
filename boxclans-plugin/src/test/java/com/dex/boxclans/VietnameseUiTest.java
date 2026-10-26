package com.dex.boxclans;

import com.dex.boxclans.FakeServer.FakePlayer;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.config.MenuConfig;
import com.dex.boxclans.config.RoleRegistry;
import com.dex.boxclans.config.Settings;
import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.gui.ClanMenu;
import com.dex.boxclans.gui.MenuAccess;
import com.dex.boxclans.text.ClanName;
import com.dex.boxclans.text.ClanNameFormatter;
import com.dex.boxclans.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 1.1.0 (BOXCLANS_SHOP_SPEC.md §1): Vietnamese texts, Danh Vọng, clan key kept inside the clan, default colour. */
class VietnameseUiTest {

    /** Small caps and the decorative symbols the server does not use (✔ ✘ ✓ ✗ ➟ ∞). */
    private static final Pattern FORBIDDEN = Pattern.compile("[ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡʏᴢѕх✔✘✓✗➟∞✦▸➥⚠]");
    private static final List<String> FILES = List.of("config.yml", "messages.yml", "icons.yml", "roles.yml",
            "points.yml", "sounds.yml", "perks.yml");

    @TempDir
    File dir;
    FakeServer s;
    FakePlayer a;
    FakePlayer c;

    @BeforeEach
    void setUp() {
        s = new FakeServer(dir);
        a = s.join("Alice");
        c = s.join("Carl");
    }

    @AfterEach
    void tearDown() {
        s.close();
    }

    private static List<String> allBundled() {
        List<String> out = new ArrayList<>(FILES);
        for (String m : MenuConfig.MENUS) out.add(MenuConfig.DIR + m + ".yml");
        return out;
    }

    private String read(String resource) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private YamlConfiguration yaml(String resource) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return y;
    }

    @Test
    void bundledTextsHaveNoSmallCapsOrDecorativeSymbols() throws Exception {
        for (String file : allBundled()) {
            int n = 0;
            for (String line : read(file).split("\n")) {
                n++;
                if (line.trim().startsWith("#")) continue; // comments may name the old wording
                assertFalse(FORBIDDEN.matcher(line).find(), file + ":" + n + " " + line);
            }
        }
    }

    @Test
    void displayFilesCarryATextVersion() throws Exception {
        List<String> text = new ArrayList<>(List.of("messages.yml", "icons.yml"));
        for (String m : MenuConfig.MENUS) text.add(MenuConfig.DIR + m + ".yml");
        for (String file : text) assertTrue(yaml(file).getInt(YamlFiles.TEXT_VERSION) >= 2, file);
        // 1.2.0 added the Minh Huy line to the main menu, 1.3.0 the storage button, 1.4.0 the history button,
        // 1.4.1 moved both into Quản lý bang hội, 1.4.2 back to the main menu with the history for everyone,
        // 1.4.3 info flag lore (description block) and history lore, 1.4.4 the owner's bottom row
        assertEquals(9, yaml("menus/clan-detail.yml").getInt(YamlFiles.TEXT_VERSION));
    }

    @Test
    void clanKeyOnlyShownInsideTheClan() throws Exception {
        // menus anyone can open: no %key%
        for (String menu : List.of("clan-list", "clan-preview", "invites", "leaderboard")) {
            String file = read(MenuConfig.DIR + menu + ".yml");
            for (String line : file.split("\n")) {
                if (line.trim().startsWith("#")) continue;
                assertFalse(line.contains("%key%"), menu + ": " + line);
            }
        }
        // the clan's own menu shows it
        assertTrue(yaml("menus/clan-detail.yml").getStringList("items.I.lore").stream()
                .anyMatch(l -> l.contains("Mã bang hội") && l.contains("%key%")));
        // public chat lists: no key; invites: the key only inside the click action
        YamlConfiguration msg = yaml("messages.yml");
        for (String key : List.of("list-entry", "top-entry")) {
            for (String line : msg.getStringList(key)) assertFalse(line.contains("%key%"), key);
        }
        for (String key : List.of("invite-received.chat.text", "invites-entry.chat.text")) {
            for (String line : msg.getStringList(key)) {
                String visible = line.replaceAll("<click:[^>]*>", "");
                assertFalse(visible.contains("%key%"), key + ": " + line);
            }
        }
    }

    @Test
    void consoleListAndTopHaveNoKey() {
        create(a, "Beta Team");
        FakeServer.CONSOLE.clear();
        s.clan.onCommand(s.console, s.command, "clan", new String[]{"list"});
        s.clan.onCommand(s.console, s.command, "clan", new String[]{"top"});
        assertTrue(FakeServer.CONSOLE.stream().anyMatch(m -> m.contains("Beta Team")), FakeServer.CONSOLE.toString());
        assertTrue(FakeServer.CONSOLE.stream().noneMatch(m -> m.contains("BetaTeam")), FakeServer.CONSOLE.toString());
    }

    @Test
    void inviteShowsTheColouredNameAndTheButtonsStillUseTheKey() {
        create(a, "&c&lRồng &6Đỏ");
        c.clear();
        s.clan(a, "invite", "Carl");
        assertTrue(c.got("Alice mời bạn vào bang hội Rồng Đỏ"), c.messages.toString());
        assertTrue(c.messages.stream().noneMatch(m -> m.contains("RongDo")), "the key is not printed: " + c.messages);
        assertTrue(c.messages.stream().noneMatch(m -> m.contains("/clan accept")), c.messages.toString());
        List<ClickEvent> clicks = new ArrayList<>();
        for (Component m : c.components) clicks(m, clicks);
        assertTrue(clicks.stream().anyMatch(e -> e.value().equals("/clan accept RongDo")), clicks.toString());
        assertTrue(clicks.stream().anyMatch(e -> e.value().equals("/clan deny RongDo")), clicks.toString());
        // the name keeps its colour: "Rồng" is red in the invite
        assertEquals(NamedTextColor.RED, colourOf(c.components.get(0), "Rồng"));
        s.clan(c, "accept", "RongDo");
        assertTrue(s.manager.of(a.id).isMember(c.id), "/clan accept <mã> still works");
    }

    @Test
    void createPromptHasTheClickableColourLink() {
        s.clan(a, "create");
        List<ClickEvent> clicks = new ArrayList<>();
        for (Component m : a.components) clicks(m, clicks);
        assertTrue(clicks.stream().anyMatch(e -> e.action() == ClickEvent.Action.OPEN_URL
                && e.value().equals("https://www.birdflop.com/resources/rgb/")), clicks.toString());
        assertTrue(a.got("Các mã màu được hỗ trợ:"), a.messages.toString());
        assertFalse(a.got("Ví dụ"));
        assertFalse(a.got("gradient"));
    }

    @Test
    void uncolouredNamesAreGold() {
        ClanName plain = ClanNameFormatter.render("Rồng Đỏ");
        assertEquals("§6Rồng Đỏ", plain.legacy());
        assertEquals("RongDo", plain.key(), "the key does not change");
        assertEquals("Rồng Đỏ", plain.raw(), "the stored name does not change");
        ClanName partly = ClanNameFormatter.render("Rồng &cĐỏ");
        assertEquals(NamedTextColor.GOLD, colourOf(partly.component(), "Rồng"));
        assertEquals(NamedTextColor.RED, colourOf(partly.component(), "Đỏ"));
        assertEquals("§cRồng Đỏ", ClanNameFormatter.render("&cRồng Đỏ").legacy(), "a coloured name keeps its colour");
        // existing clans: the colour comes from rendering, so a clan loaded from the database is gold too
        ClanData clan = create(a, "Beta Team");
        assertEquals(NamedTextColor.GOLD, colourOf(clan.name().component(), "Beta"));
    }

    @Test
    void mainMenuHasNoPaging() {
        create(a, "Alpha");
        s.clan(a);
        assertInstanceOf(ClanMenu.class, a.menu);
        assertTrue(MenuAccess.slots(a.menu, 'P').isEmpty());
        assertTrue(MenuAccess.slots(a.menu, 'N').isEmpty());
        assertEquals(45, MenuAccess.slots(a.menu, 'X').size());
    }

    @Test
    void rolesUseTheirGuiColourInMenusAndKeepTheChatColour() throws Exception {
        RoleRegistry roles = RoleRegistry.from(yaml("roles.yml"), FakeServer.LOG);
        RoleRegistry.RoleInfo leader = roles.info(ClanRole.LEADER);
        assertEquals("§c", leader.colorLegacy(), "chat colour unchanged");
        // 1.4.3: menus and messages show the Minh Chủ in a gold-orange gradient, clan chat keeps &c
        assertEquals("<gradient:#FFD600:#FF8C00>Minh Chủ</gradient>", leader.guiStyled());
        Component gui = Text.parse(s.plugin.actions().guiRole(ClanRole.LEADER).value());
        assertEquals("Minh Chủ", Text.plain(gui));
        assertEquals(TextColor.fromHexString("#FFD600"), colourOf(gui, "M"), "starts gold");
        assertEquals(s.plugin.actions().guiRole(ClanRole.LEADER), s.plugin.actions().role(ClanRole.LEADER),
                "messages use the menu style");
        assertEquals(NamedTextColor.RED, colourOf(Text.parse(leader.colorMini() + "Minh Chủ"), "Minh Chủ"),
                "clan chat colour");
        assertEquals("<#5CD6FF>Phó Bang", roles.info(ClanRole.VICE).guiStyled());
        // a roles.yml still holding the old default gets the gradient, an edited colour is kept
        YamlConfiguration oldDefault = yaml("roles.yml");
        oldDefault.set("roles.MinhChu.gui-color", "&#FF5C5C");
        assertEquals(leader.guiStyled(), RoleRegistry.from(oldDefault, FakeServer.LOG).info(ClanRole.LEADER).guiStyled());
        YamlConfiguration edited = yaml("roles.yml");
        edited.set("roles.MinhChu.gui-color", "&#AA00FF");
        assertEquals("<#AA00FF>Minh Chủ", RoleRegistry.from(edited, FakeServer.LOG).info(ClanRole.LEADER).guiStyled());
        // a roles.yml without gui-color falls back to color
        YamlConfiguration old = yaml("roles.yml");
        old.set("roles.MinhChu.gui-color", null);
        old.setDefaults(new YamlConfiguration());
        assertEquals(Text.legacyToMini("&c"), RoleRegistry.from(old, FakeServer.LOG).info(ClanRole.LEADER).guiColorMini());
    }

    @Test
    void oldDefaultChatFormatsAreUpgradedAndCustomOnesKept() throws Exception {
        YamlConfiguration bundled = yaml("config.yml");
        YamlConfiguration file = new YamlConfiguration();
        file.set("chat.clan-format", "&#FFC700ᴄʟᴀɴ &7| %role_color%(%role%) %player%&7: &e%message%");
        file.set("chat.spy-format", "&#FF5555ꜱᴘʏ &7| %clan% %role_color%(%role%) %player%&7: &e%message%");
        file.setDefaults(bundled);
        Settings upgraded = Settings.from(file);
        assertEquals(bundled.getString("chat.clan-format"), upgraded.clanChatFormat());
        assertEquals(bundled.getString("chat.spy-format"), upgraded.spyFormat());
        file.set("chat.clan-format", "&aCLAN &7| %player%: %message%");
        assertEquals("&aCLAN &7| %player%: %message%", Settings.from(file).clanChatFormat(), "an edited format is kept");
    }

    @Test
    void olderTextFilesAreMovedAsideAndReplaced() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dir);
        when(plugin.getLogger()).thenReturn(FakeServer.LOG);
        when(plugin.getResource(anyString())).thenAnswer(i -> getClass().getClassLoader().getResourceAsStream(i.getArgument(0)));
        doAnswer(i -> {
            File out = new File(dir, (String) i.getArgument(0));
            out.getParentFile().mkdirs();
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(i.getArgument(0))) {
                Files.copy(in, out.toPath());
            }
            return null;
        }).when(plugin).saveResource(anyString(), anyBoolean());

        // a 1.0.0 file (no text-version): moved to old-configs/v1/ and replaced
        File messages = new File(dir, "messages.yml");
        Files.writeString(messages.toPath(), "prefix: 'OLD'\nno-permission:\n  chat:\n    text: 'cũ'\n");
        YamlConfiguration loaded = YamlFiles.loadText(plugin, "messages.yml");
        int current = yaml("messages.yml").getInt(YamlFiles.TEXT_VERSION);
        assertEquals(current, loaded.getInt(YamlFiles.TEXT_VERSION));
        assertFalse(loaded.getString("prefix").contains("OLD"));
        File old = new File(dir, "old-configs/v1/messages.yml");
        assertTrue(old.exists());
        assertTrue(Files.readString(old.toPath()).contains("'OLD'"), "the old copy is kept");

        // a second old copy does not overwrite the first one
        Files.writeString(messages.toPath(), "prefix: 'OLD2'\n");
        YamlFiles.loadText(plugin, "messages.yml");
        assertTrue(Files.readString(old.toPath()).contains("'OLD'"), "the first copy is untouched");
        File[] copies = old.getParentFile().listFiles((d, n) -> n.startsWith("messages.yml."));
        assertNotNull(copies);
        assertEquals(1, copies.length);
        assertTrue(Files.readString(copies[0].toPath()).contains("'OLD2'"));

        // a file already at version 2 is the owner's: never replaced
        Files.writeString(messages.toPath(), "text-version: " + current + "\nprefix: 'MINE'\n");
        assertEquals("MINE", YamlFiles.loadText(plugin, "messages.yml").getString("prefix"));
        // menus in sub-folders too
        File menu = new File(dir, "menus/settings.yml");
        menu.getParentFile().mkdirs();
        Files.writeString(menu.toPath(), "title: 'OLD'\n");
        assertEquals("Cài đặt bang hội", YamlFiles.loadText(plugin, "menus/settings.yml").getString("title"));
        assertTrue(new File(dir, "old-configs/v1/menus/settings.yml").exists());
        // a file never created is simply written
        assertTrue(YamlFiles.loadText(plugin, "icons.yml").getInt(YamlFiles.TEXT_VERSION) >= 2);
    }

    @Test
    void danhVongIsAnotherNameOfThePointsCommand() {
        ClanData clan = create(a, "Alpha");
        s.admin(s.console, "danhvong", "add", "Alpha", "10", "event");
        assertEquals(10, clan.points());
        s.admin(s.console, "points", "add", "Alpha", "5", "event");
        assertEquals(15, clan.points());
    }

    // ------------------------------------------------------------------ helpers

    private ClanData create(FakePlayer p, String name) {
        s.clan(p, "create");
        s.type(p, name);
        p.click('C');
        return s.manager.of(p.id);
    }

    private static void clicks(Component c, List<ClickEvent> out) {
        if (c.clickEvent() != null) out.add(c.clickEvent());
        for (Component child : c.children()) clicks(child, out);
    }

    /** Colour of the first text component containing {@code text}, inherited from its parents when unset. */
    private static TextColor colourOf(Component c, String text) {
        return colourOf(c, text, null);
    }

    private static TextColor colourOf(Component c, String text, TextColor inherited) {
        TextColor colour = c.color() != null ? c.color() : inherited;
        if (c instanceof TextComponent t && t.content().contains(text)) return colour;
        for (Component child : c.children()) {
            TextColor found = colourOf(child, text, colour);
            if (found != null) return found;
        }
        return null;
    }
}
