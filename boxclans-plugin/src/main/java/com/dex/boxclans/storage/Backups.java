package com.dex.boxclans.storage;

import com.dex.boxclans.config.Settings;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Daily backup of {@code clans.db} into {@code backups/clans-<yyyy-MM-dd>.db}. {@link #runIfDue} is called at start-up
 * and every hour: when today's copy is missing it is made on the DB thread ({@link Database#backup}), so the server
 * never waits for the disk. The copy is written to a {@code .tmp} file first and renamed when complete, so a crash
 * never leaves a half-written file that looks like a good backup. The oldest copies beyond {@code keep} are deleted.
 */
public final class Backups {

    private static final Pattern NAME = Pattern.compile("clans-\\d{4}-\\d{2}-\\d{2}\\.db");

    private final Database db;
    private final File dir;
    private final Supplier<Settings.Backup> config;
    private final Supplier<ZoneId> zone;
    private final LongSupplier clock;
    private final Logger logger;
    private final AtomicBoolean running = new AtomicBoolean();

    public Backups(Database db, File dataFolder, Supplier<Settings.Backup> config, Supplier<ZoneId> zone,
                   LongSupplier clock, Logger logger) {
        this.db = db;
        this.dir = new File(dataFolder, "backups");
        this.config = config;
        this.zone = zone;
        this.clock = clock;
        this.logger = logger;
    }

    /** The backup file of a day. */
    public File fileOf(LocalDate day) {
        return new File(dir, "clans-" + day + ".db");
    }

    /** Main thread: starts today's backup unless it exists, backups are off, or one is already running. */
    public void runIfDue() {
        Settings.Backup cfg = config.get();
        if (!cfg.enabled()) return;
        File target = fileOf(LocalDate.ofInstant(Instant.ofEpochMilli(clock.getAsLong()), zone.get()));
        if (target.exists() || !running.compareAndSet(false, true)) return;
        if (!dir.isDirectory() && !dir.mkdirs()) {
            running.set(false);
            logger.warning("Could not create " + dir + ": clans.db is not backed up");
            return;
        }
        File tmp = new File(dir, target.getName() + ".tmp");
        if (tmp.exists() && !tmp.delete()) {
            running.set(false);
            logger.warning("Could not delete the unfinished backup " + tmp.getName());
            return;
        }
        db.backup(tmp).whenComplete((ok, error) -> {
            try {
                if (error != null) {
                    tmp.delete();
                    logger.log(Level.WARNING, "Backup of clans.db failed", error);
                    return;
                }
                move(tmp, target);
                prune(cfg.keep());
                logger.info("Backed up clans.db to backups/" + target.getName());
            } catch (IOException ex) {
                tmp.delete();
                logger.log(Level.WARNING, "Could not finish the backup " + target.getName(), ex);
            } finally {
                running.set(false);
            }
        });
    }

    private static void move(File from, File to) throws IOException {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(from.toPath(), to.toPath());
        }
    }

    /** Deletes the oldest daily copies so that {@code keep} remain (names sort by date). */
    void prune(int keep) {
        File[] files = dir.listFiles((d, name) -> NAME.matcher(name).matches());
        if (files == null || files.length <= keep) return;
        List<File> sorted = new ArrayList<>(Arrays.asList(files));
        sorted.sort(Comparator.comparing(File::getName).reversed());
        for (File old : sorted.subList(keep, sorted.size())) {
            if (!old.delete()) logger.warning("Could not delete the old backup " + old.getName());
        }
    }

    /** Backups currently in the folder, newest first (tests, diagnostics). */
    public List<String> list() {
        File[] files = dir.listFiles((d, name) -> NAME.matcher(name).matches());
        if (files == null) return List.of();
        return Arrays.stream(files).map(File::getName).sorted(Comparator.reverseOrder()).toList();
    }
}
