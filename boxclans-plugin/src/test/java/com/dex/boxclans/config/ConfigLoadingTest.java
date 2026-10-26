package com.dex.boxclans.config;

import com.dex.boxclans.text.Text;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoadingTest {

    private static final Logger LOG = Logger.getLogger("test");

    static YamlConfiguration bundled(String name, File file) {
        InputStream in = ConfigLoadingTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(in, name + " must be bundled");
        return YamlFiles.load(file, in, LOG);
    }

    @Test
    void bundledFilesParse(@TempDir Path dir) {
        Settings s = Settings.from(bundled("config.yml", dir.resolve("missing.yml").toFile()));
        assertEquals(3, s.nameMinLength());
        assertEquals(16, s.nameMaxLength());
        assertEquals(300, s.inviteExpirySeconds());
        assertEquals(10, s.inviteMaxPending());
        assertTrue(s.antiFarm().sameIp());
        Messages m = Messages.from(bundled("messages.yml", dir.resolve("missing2.yml").toFile()));
        assertTrue(m.has("no-permission"));
        Sounds sounds = Sounds.from(bundled("sounds.yml", dir.resolve("missing3.yml").toFile()), LOG);
        assertNotNull(sounds.spec("ui-click"));
    }

    @Test
    void missingKeysFallBackToBundled(@TempDir Path dir) throws Exception {
        File file = dir.resolve("messages.yml").toFile();
        Files.writeString(file.toPath(), """
                prefix: '&aP '
                no-permission:
                  chat:
                    enabled: true
                    text: '&cNope %x%'
                  actionbar:
                    enabled: true
                    text: 'bar'
                """, StandardCharsets.UTF_8);
        Messages m = Messages.from(bundled("messages.yml", file));
        assertEquals("<green>P ", m.prefix());
        assertEquals("<red>Nope %x%", m.entry("no-permission").chat());
        assertEquals("bar", m.entry("no-permission").actionbar());
        // not in the user file: comes from the jar
        assertNotNull(m.entry("player-only").chat());
        assertNull(m.entry("player-only").actionbar());
        assertEquals("P Nope 1", Text.plain(m.chat("no-permission", "x", 1)));
    }

    @Test
    void brokenFileUsesBundledValues(@TempDir Path dir) throws Exception {
        File file = dir.resolve("config.yml").toFile();
        Files.writeString(file.toPath(), "invite: [unclosed", StandardCharsets.UTF_8);
        Settings s = Settings.from(bundled("config.yml", file));
        assertEquals(300, s.inviteExpirySeconds());
    }
}
