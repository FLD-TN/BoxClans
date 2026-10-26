package com.dex.boxclans;

import com.dex.boxclans.FakeServer.FakePlayer;
import com.dex.boxclans.config.Settings;
import com.dex.boxclans.gui.MenuAccess;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.4.3: settings layout, description length and wrapping, info flag lore. */
class Ui143Test {

    @TempDir
    File dir;
    FakeServer s;
    FakePlayer alice;

    @BeforeEach
    void setUp() {
        s = new FakeServer(dir);
        alice = s.join("Alice");
        s.clan(alice, "create");
        s.type(alice, "Alpha");
        alice.click('C');
    }

    @AfterEach
    void tearDown() {
        s.close();
    }

    private static YamlConfiguration bundled(String name) throws Exception {
        try (var in = Ui143Test.class.getClassLoader().getResourceAsStream(name)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void settingsFirstRow() {
        s.clan(alice, "settings");
        assertEquals(List.of(0), MenuAccess.slots(alice.menu, 'R'), "rename");
        assertEquals(List.of(1), MenuAccess.slots(alice.menu, 'F'), "flag");
        assertEquals(List.of(2), MenuAccess.slots(alice.menu, 'I'), "icon");
        assertEquals(List.of(5), MenuAccess.slots(alice.menu, 'V'));
        assertEquals(List.of(6), MenuAccess.slots(alice.menu, 'C'));
        assertEquals(List.of(7), MenuAccess.slots(alice.menu, 'S'));
        assertEquals(List.of(8), MenuAccess.slots(alice.menu, 'M'));
    }

    @Test
    void descriptionIsAtMostFiftyAndTheOld64Default() throws Exception {
        assertEquals(50, s.plugin.settings().descriptionMaxLength());
        YamlConfiguration old = new YamlConfiguration();
        old.set("clan-description.max-length", 64);
        old.setDefaults(bundled("config.yml"));
        assertEquals(50, Settings.from(old).descriptionMaxLength(), "the old default becomes 50");
        old.set("clan-description.max-length", 80);
        assertEquals(80, Settings.from(old).descriptionMaxLength(), "an edited value is kept");
    }

    @Test
    void descriptionWraps() {
        assertEquals(List.of("Bang hội mạnh nhất server, tuyển", "thành viên chăm chỉ"),
                MenuAccess.wrap("Bang hội mạnh nhất server, tuyển thành viên chăm chỉ", 32));
        assertEquals(List.of("Bang hội mạnh nhất server,", "tuyển thành viên chăm chỉ"),
                MenuAccess.wrap("Bang hội mạnh nhất server, tuyển thành viên chăm chỉ", 30));
        assertEquals(List.of("aaaaaaaaaa", "aaaaa b"), MenuAccess.wrap("aaaaaaaaaaaaaaa b", 10), "a long word is cut");
        assertEquals(List.of("Chưa có mô tả."), MenuAccess.wrap("  Chưa có   mô tả.  ", 30));
        for (String line : MenuAccess.wrap("x".repeat(50), 30)) assertTrue(line.length() <= 30, line);
    }

    @Test
    void infoFlagLoreOrder() throws Exception {
        List<String> lore = bundled("menus/clan-detail.yml").getStringList("items.I.lore");
        assertTrue(lore.get(0).contains("Mã bang hội"), lore.toString());
        assertEquals("", lore.get(1));
        assertEquals("%description_lines%", lore.get(3));
        assertEquals("", lore.get(4));
        assertTrue(lore.get(5).contains("<gradient:#FFD600:#FF8C00>%leader%</gradient>"), lore.get(5));
        List<String> history = bundled("menus/clan-detail.yml").getStringList("items.G.lore");
        for (String line : history) assertTrue(line.replaceAll("&#[0-9A-Fa-f]{6}", "").length() <= 32, line);
    }
}
