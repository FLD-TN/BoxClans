package com.dex.boxclans.points;

import com.dex.boxclans.storage.Database;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Points earned per day for {@code daily-cap} / {@code clan-daily-cap} (§5.1, §5.3), kept in RAM and mirrored to
 * {@code points_daily}. The day turns at 0h in {@code day-reset-timezone}; a new day starts every counter at 0.
 * Main thread only, except {@link #peek}.
 */
public final class DailyCounters {

    private final Database db;
    private final LongSupplier clock;
    private volatile ZoneId zone;
    private volatile String day;
    /** Written on the main thread, read by placeholders from any thread ({@link #peek}). */
    private final Map<String, Long> counts = new ConcurrentHashMap<>();
    /** Clan caps already announced today ({@code owner|source}). */
    private final Set<String> announced = new HashSet<>();

    public DailyCounters(Database db, LongSupplier clock, ZoneId zone) {
        this.db = db;
        this.clock = clock;
        this.zone = zone;
        this.day = today();
    }

    public static String player(UUID player) {
        return "p:" + player;
    }

    public static String clan(UUID clan) {
        return "c:" + clan;
    }

    public void zone(ZoneId zone) {
        this.zone = zone;
        roll();
    }

    /** Loads today's counters (start-up). */
    public void load() throws SQLException {
        day = today();
        counts.clear();
        counts.putAll(db.loadDaily(day));
    }

    public String today() {
        return LocalDate.ofInstant(Instant.ofEpochMilli(clock.getAsLong()), zone).toString();
    }

    /** Starts a new day when 0h has passed. */
    public void roll() {
        String t = today();
        if (!t.equals(day)) {
            day = t;
            counts.clear();
            announced.clear();
        }
    }

    public long get(String owner, String source) {
        roll();
        return counts.getOrDefault(owner + "|" + source, 0L);
    }

    public void add(String owner, String source, long amount) {
        roll();
        long total = counts.merge(owner + "|" + source, amount, Long::sum);
        db.saveDaily(owner, source, day, total);
    }

    /** Thread-safe read for placeholders: 0 once the day has turned (the main thread rolls it later). */
    public long peek(String owner, String source) {
        if (!today().equals(day)) return 0;
        return counts.getOrDefault(owner + "|" + source, 0L);
    }

    /** True the first time it is called for this owner and source today. */
    public boolean firstAnnounce(String owner, String source) {
        roll();
        return announced.add(owner + "|" + source);
    }

    /** Clears the counters of a clan (points reset). */
    public void clearOwner(String owner) {
        counts.keySet().removeIf(k -> k.startsWith(owner + "|"));
    }
}
