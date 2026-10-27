package com.dex.boxclans.points;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PointsMigrationTest {

    private static final Logger LOG = Logger.getLogger("test");

    /** A points.yml of config-version 1 the owner edited (kill points, a comment, an own source). */
    private static final String OLD = """
            # owner comment kept
            config-version: 1
            allow-negative: false
            sources:
              kill:
                points: 25          # owner changed this
                display: 'Hạ gục người chơi'
              my-event:
                points: 5

            # Sự kiện kill
            kill-events:
              loan-chien:
                kill: 10
            """;

    @TempDir
    Path dir;

    private static String bundled() throws IOException {
        try (InputStream in = PointsMigrationTest.class.getClassLoader().getResourceAsStream("points.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private File write(String text) throws IOException {
        File f = dir.resolve("points.yml").toFile();
        Files.writeString(f.toPath(), text, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void addsNewSourcesAndKeepsTheOwnersFile() throws Exception {
        File f = write(OLD);
        assertTrue(PointsMigration.upgrade(f, bundled(), dir.resolve("old-configs").toFile(), LOG));

        String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        assertEquals(2, y.getInt("config-version"));
        assertEquals(25, y.getInt("sources.kill.points"), "owner values stay");
        assertEquals(5, y.getInt("sources.my-event.points"));
        assertEquals("Top bang hội đánh boss", y.getString("sources.mob-boss.display"));
        assertEquals("Hạ gục boss", y.getString("sources.mob-kill.display"));
        assertEquals(10, y.getInt("kill-events.loan-chien.kill"), "sections after sources stay");
        assertTrue(text.contains("# owner comment kept") && text.contains("# owner changed this"));
        assertTrue(text.contains("# --- MyBoxMobs"), "the comment above the new sources comes along");
        assertTrue(text.indexOf("mob-kill:") < text.indexOf("# Sự kiện kill"));
        assertTrue(dir.resolve("old-configs/v1/points.yml").toFile().exists(), "old copy kept");

        assertFalse(PointsMigration.upgrade(f, bundled(), dir.resolve("old-configs").toFile(), LOG),
                "already current: nothing to do");
    }

    @Test
    void doesNotDuplicateASourceTheOwnerAlreadyAdded() throws Exception {
        File f = write(OLD.replace("  my-event:", "  mob-boss:\n    points: 3\n  my-event:"));
        assertTrue(PointsMigration.upgrade(f, bundled(), dir.resolve("old-configs").toFile(), LOG));
        String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        assertEquals(text.indexOf("mob-boss:"), text.lastIndexOf("mob-boss:"));
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        assertEquals(3, y.getInt("sources.mob-boss.points"));
        assertEquals("Hạ gục boss", y.getString("sources.mob-kill.display"));
    }

    @Test
    void keepsWindowsLineEndings() throws Exception {
        File f = write(OLD.replace("\n", "\r\n"));
        PointsMigration.upgrade(f, bundled(), dir.resolve("old-configs").toFile(), LOG);
        String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        assertFalse(text.replace("\r\n", "").contains("\n"), "every line ends with CRLF");
    }
}
