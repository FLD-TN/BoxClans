package com.dex.boxclans.storage;

import com.dex.boxclans.config.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Daily {@code VACUUM INTO} copy of clans.db, written through a .tmp file, the oldest beyond {@code keep} deleted. */
class BackupsTest {

    private static final Logger LOG = Logger.getLogger("test");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 2026-10-20 03:00 UTC = 10:00 in Ho Chi Minh. */
    private static final long OCT_20 = 1_792_465_200_000L;

    @TempDir
    File dir;
    Database db;
    Backups backups;
    final AtomicLong clock = new AtomicLong(OCT_20);
    final AtomicReference<Settings.Backup> config = new AtomicReference<>(new Settings.Backup(true, 3));

    @BeforeEach
    void setUp() {
        db = Database.open(new File(dir, Database.FILE), LOG);
        backups = new Backups(db, dir, config::get, () -> ZONE, clock::get, LOG);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    /** Waits until the DB thread finished the backup and its completion step. */
    private void settle() throws Exception {
        db.submit(c -> null).get(10, TimeUnit.SECONDS);
    }

    @Test
    void makesTodaysCopyOnceAndKeepsTheNewest() throws Exception {
        File folder = new File(dir, "backups");
        assertTrue(folder.mkdirs());
        for (int day = 1; day <= 5; day++) {
            Files.writeString(new File(folder, "clans-2026-10-0" + day + ".db").toPath(), "old");
        }
        Files.writeString(new File(folder, "notes.txt").toPath(), "not a backup");

        backups.runIfDue();
        settle();
        assertEquals(List.of("clans-2026-10-20.db", "clans-2026-10-05.db", "clans-2026-10-04.db"), backups.list());
        assertTrue(new File(folder, "notes.txt").exists(), "other files are never touched");
        assertFalse(new File(folder, "clans-2026-10-20.db.tmp").exists());

        // a real SQLite copy with the tables
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + backups.fileOf(LocalDate.of(2026, 10, 20)));
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM clans")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }

        // same day again: nothing new
        long size = backups.fileOf(LocalDate.of(2026, 10, 20)).length();
        backups.runIfDue();
        settle();
        assertEquals(size, backups.fileOf(LocalDate.of(2026, 10, 20)).length());
        assertEquals(3, backups.list().size());
    }

    @Test
    void theNextDayMakesANewCopy() throws Exception {
        backups.runIfDue();
        settle();
        clock.addAndGet(86_400_000L);
        backups.runIfDue();
        settle();
        assertEquals(List.of("clans-2026-10-21.db", "clans-2026-10-20.db"), backups.list());
    }

    @Test
    void disabledMakesNothing() throws Exception {
        config.set(new Settings.Backup(false, 3));
        backups.runIfDue();
        settle();
        assertEquals(List.of(), backups.list());
    }

    @Test
    void aLeftoverTmpFromACrashIsReplaced() throws Exception {
        File folder = new File(dir, "backups");
        assertTrue(folder.mkdirs());
        Files.writeString(new File(folder, "clans-2026-10-20.db.tmp").toPath(), "half written");
        backups.runIfDue();
        settle();
        assertEquals(List.of("clans-2026-10-20.db"), backups.list());
        assertFalse(new File(folder, "clans-2026-10-20.db.tmp").exists());
    }
}
