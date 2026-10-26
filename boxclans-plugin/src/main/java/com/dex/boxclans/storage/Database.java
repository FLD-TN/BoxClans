package com.dex.boxclans.storage;

import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.text.ClanName;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite storage, {@code plugins/BoxClans/clans.db} (§11). One single-thread executor ({@code BoxClans-DB})
 * owns the connection. Clans are loaded once at start; every change is a queued write that captures its values
 * on the calling (main) thread and is group-committed on the DB thread, in order. No table holds items.
 */
public final class Database {

    @FunctionalInterface
    public interface SqlTask<T> {
        T run(Connection connection) throws SQLException;
    }

    /** A clan row as stored, plus its members. */
    public record ClanRow(UUID uuid, String key, String name, String flag, String description, UUID leader,
                          long createdAt, boolean friendlyFire, long points, int maxMembers, long lastRename,
                          String icon, String ownedIcons, long minhHuy) {
        public static ClanRow of(ClanData d) {
            return new ClanRow(d.uuid(), d.key(), d.name().raw(), d.flag(), d.description(), d.leader(), d.createdAt(),
                    d.friendlyFire(), d.points(), d.maxMembers(), d.lastRename(), d.icon(),
                    String.join(",", d.ownedIcons()), d.minhHuy());
        }
    }

    /**
     * One {@code clan_history} row (BOXCLANS_SHOP_SPEC.md §6.1). {@code type} is a
     * {@link com.dex.boxclans.history.HistoryType} name; names are stored as they were when the row was written.
     */
    public record HistoryRow(long id, UUID clan, long at, String type, UUID actor, String actorName, UUID target,
                             String targetName, long amount, String detail) {}

    /** One page of a clan's history, newest first, plus the number of matching rows. */
    public record HistoryPage(List<HistoryRow> rows, int total) {}

    /** One {@code shop_entries} row (BOXCLANS_SHOP_SPEC.md §4.2); {@code commands} one per line. */
    public record ShopRow(int id, String category, byte[] item, String mode, String commands, long price,
                          int weeklyLimit, int order, boolean hidden) {}

    /** One {@code vault_items} row (§5.5). */
    public record VaultRow(long id, UUID clan, String category, String mode, byte[] item, String commands, int amount,
                           long createdAt) {}

    /** @param lastSeen last time the member was online (join or quit), epoch ms; 0 = unknown */
    public record MemberRow(UUID player, UUID clan, ClanRole role, long joinedAt, long contribution, long lastSeen) {}

    /** Per-player toggles (§4B, P4). */
    public record PlayerSettings(boolean chatSound, boolean memberSound, boolean chatMode) {
        public static final PlayerSettings DEFAULT = new PlayerSettings(true, true, false);
    }

    /** One {@code points_log} row (§5.5). */
    public record LogRow(long id, long at, UUID clan, UUID player, String source, long delta, String by) {}

    public static final String FILE = "clans.db";
    private static final long TIMEOUT_SECONDS = 10;
    private static final int MAX_BATCH = 1000;

    private record Write(String what, SqlTask<?> op) {}

    private final ExecutorService executor;
    private final Connection connection;
    private final Logger logger;
    private final ConcurrentLinkedQueue<Write> writes = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();

    private Database(ExecutorService executor, Connection connection, Logger logger) {
        this.executor = executor;
        this.connection = connection;
        this.logger = logger;
    }

    /** Opens (or creates) the database; {@code null} when the driver is missing or the file cannot be used. */
    public static Database open(File file, Logger logger) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException | LinkageError ex) {
            logger.severe("SQLite JDBC driver (org.sqlite.JDBC) not found");
            return null;
        }
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BoxClans-DB");
            t.setDaemon(true);
            return t;
        });
        try {
            Connection c = executor.submit(() -> connect(file)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return new Database(executor, c, logger);
        } catch (Exception ex) {
            executor.shutdownNow();
            logger.log(Level.SEVERE, "Could not open " + file.getName() + " (the file was not modified)",
                    ex.getCause() != null ? ex.getCause() : ex);
            return null;
        }
    }

    private static Connection connect(File file) throws SQLException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new SQLException("Could not create " + parent);
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS clans (
                        uuid           TEXT    PRIMARY KEY,
                        key            TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                        name           TEXT    NOT NULL,
                        flag           TEXT    NOT NULL DEFAULT '',
                        description    TEXT    NOT NULL DEFAULT '',
                        leader         TEXT    NOT NULL,
                        created_at     INTEGER NOT NULL,
                        friendly_fire  INTEGER NOT NULL DEFAULT 0,
                        points         INTEGER NOT NULL DEFAULT 0,
                        max_members    INTEGER NOT NULL,
                        last_rename    INTEGER NOT NULL DEFAULT 0
                    )""");
            addColumn(c, "clans", "icon", "TEXT NOT NULL DEFAULT ''");
            addColumn(c, "clans", "owned_icons", "TEXT NOT NULL DEFAULT ''");
            addColumn(c, "clans", "minh_huy", "INTEGER NOT NULL DEFAULT 0");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS members (
                        player        TEXT    PRIMARY KEY,
                        clan          TEXT    NOT NULL,
                        role          TEXT    NOT NULL,
                        joined_at     INTEGER NOT NULL,
                        contribution  INTEGER NOT NULL DEFAULT 0
                    )""");
            addColumn(c, "members", "last_seen", "INTEGER NOT NULL DEFAULT 0");
            st.execute("CREATE INDEX IF NOT EXISTS members_clan ON members (clan)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS player_settings (
                        player        TEXT    PRIMARY KEY,
                        chat_sound    INTEGER NOT NULL DEFAULT 1,
                        member_sound  INTEGER NOT NULL DEFAULT 1,
                        chat_mode     INTEGER NOT NULL DEFAULT 0
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS notices (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        player      TEXT    NOT NULL,
                        message     TEXT    NOT NULL,
                        created_at  INTEGER NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS notices_player ON notices (player)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS points_log (
                        id      INTEGER PRIMARY KEY AUTOINCREMENT,
                        at      INTEGER NOT NULL,
                        clan    TEXT    NOT NULL,
                        player  TEXT,
                        source  TEXT    NOT NULL,
                        delta   INTEGER NOT NULL,
                        by      TEXT    NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS points_log_clan_at ON points_log (clan, at DESC)");
            // pruneLog deletes by age across every clan: without this index it scans the whole table
            st.execute("CREATE INDEX IF NOT EXISTS points_log_at ON points_log (at)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS clan_history (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        clan         TEXT    NOT NULL,
                        at           INTEGER NOT NULL,
                        type         TEXT    NOT NULL,
                        actor        TEXT,
                        actor_name   TEXT    NOT NULL DEFAULT '',
                        target       TEXT,
                        target_name  TEXT    NOT NULL DEFAULT '',
                        amount       INTEGER NOT NULL DEFAULT 0,
                        detail       TEXT    NOT NULL DEFAULT ''
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS clan_history_clan_at ON clan_history (clan, at DESC)");
            // pruneHistory deletes by age across every clan
            st.execute("CREATE INDEX IF NOT EXISTS clan_history_at ON clan_history (at)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS shop_entries (
                        id            INTEGER PRIMARY KEY,
                        category      TEXT    NOT NULL,
                        item          BLOB    NOT NULL,
                        mode          TEXT    NOT NULL,
                        commands      TEXT    NOT NULL DEFAULT '',
                        price         INTEGER NOT NULL,
                        weekly_limit  INTEGER NOT NULL DEFAULT 0,
                        ord           INTEGER NOT NULL DEFAULT 0,
                        hidden        INTEGER NOT NULL DEFAULT 0
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS shop_purchases (
                        clan   TEXT    NOT NULL,
                        entry  INTEGER NOT NULL,
                        week   INTEGER NOT NULL,
                        count  INTEGER NOT NULL,
                        PRIMARY KEY (clan, entry, week)
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS vault_items (
                        id          INTEGER PRIMARY KEY,
                        clan        TEXT    NOT NULL,
                        category    TEXT    NOT NULL,
                        mode        TEXT    NOT NULL,
                        item        BLOB    NOT NULL,
                        commands    TEXT    NOT NULL DEFAULT '',
                        amount      INTEGER NOT NULL,
                        created_at  INTEGER NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS vault_items_clan ON vault_items (clan)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS points_daily (
                        owner   TEXT    NOT NULL,
                        source  TEXT    NOT NULL,
                        day     TEXT    NOT NULL,
                        total   INTEGER NOT NULL,
                        PRIMARY KEY (owner, source, day)
                    )""");
        } catch (SQLException ex) {
            try {
                c.close();
            } catch (SQLException ignored) {
                // already failing
            }
            throw ex;
        }
        return c;
    }

    /** Migration: adds a column to an existing table when it is missing (old databases keep their rows). */
    private static void addColumn(Connection c, String table, String column, String definition) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (column.equalsIgnoreCase(rs.getString("name"))) return;
        }
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    // ------------------------------------------------------------------ plumbing

    /** Runs a task on the DB thread after every write queued before it. */
    public <T> CompletableFuture<T> submit(SqlTask<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                flush();
                try {
                    future.complete(task.run(connection));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException ex) {
            future.completeExceptionally(ex);
        }
        return future;
    }

    /** Runs a task and waits for it (start-up loads only). */
    public <T> T call(SqlTask<T> task) throws SQLException {
        try {
            return submit(task).get(TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof SQLException sql) throw sql;
            throw new SQLException(cause);
        }
    }

    /** Writes queued by {@link #atomically} on its thread, {@code null} outside one. */
    private final ThreadLocal<List<Write>> group = new ThreadLocal<>();

    /**
     * Runs {@code body} and commits every write it queues in one transaction (1.4.4): a purchase never ends up with
     * the Minh Huy taken but the item missing, even if the server stops in between. Nested calls join the outer one.
     */
    public void atomically(String what, Runnable body) {
        if (group.get() != null) {
            body.run();
            return;
        }
        List<Write> ops = new ArrayList<>();
        group.set(ops);
        try {
            body.run();
        } finally {
            group.remove();
            if (!ops.isEmpty()) {
                write(what, c -> {
                    for (Write w : ops) w.op().run(c);
                    return null;
                });
            }
        }
    }

    /** Queues a write; {@code what} describes it for the failure log. Any thread. */
    public void write(String what, SqlTask<?> op) {
        List<Write> grouped = group.get();
        if (grouped != null) {
            grouped.add(new Write(what, op));
            return;
        }
        writes.add(new Write(what, op));
        if (flushScheduled.compareAndSet(false, true)) {
            try {
                executor.execute(() -> {
                    flushScheduled.set(false);
                    flush();
                });
            } catch (RejectedExecutionException ex) {
                flushScheduled.set(false);
                Write w;
                while ((w = writes.poll()) != null) logger.severe("Clan write lost (database closed): " + w.what());
            }
        }
    }

    /** DB thread (or after shutdown): commits every queued write in order. */
    private void flush() {
        List<Write> batch = new ArrayList<>();
        while (true) {
            batch.clear();
            Write w;
            while (batch.size() < MAX_BATCH && (w = writes.poll()) != null) batch.add(w);
            if (batch.isEmpty()) return;
            try {
                inTransaction(c -> {
                    for (Write x : batch) x.op().run(c);
                    return null;
                });
            } catch (Throwable batchError) {
                for (Write x : batch) {
                    try {
                        inTransaction(c -> x.op().run(c));
                    } catch (Throwable t) {
                        logger.log(Level.SEVERE, "Clan write failed: " + x.what(), t);
                    }
                }
            }
        }
    }

    private <T> T inTransaction(SqlTask<T> task) throws SQLException {
        connection.setAutoCommit(false);
        try {
            T result = task.run(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException ex) {
            try {
                connection.rollback();
            } catch (SQLException suppressed) {
                ex.addSuppressed(suppressed);
            }
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /** Commits every queued write, then closes the connection. */
    public void close() {
        executor.shutdown();
        boolean finished = false;
        try {
            finished = executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (finished) {
            flush();
        } else {
            executor.shutdownNow();
            logger.severe("Clan writes did not finish in time: the last changes may be lost");
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Could not close " + FILE, ex);
        }
    }

    /**
     * Writes a consistent copy of the whole database to {@code target} ({@code VACUUM INTO}), on the DB thread after
     * every write queued before it. The target must not exist. Other writes wait while the copy runs.
     */
    public CompletableFuture<Void> backup(File target) {
        String path = target.getAbsolutePath().replace("'", "''");
        return submit(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("VACUUM INTO '" + path + "'");
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ clans and members

    /**
     * Loads every clan with its members. {@code names} rebuilds a {@link ClanName} from the stored text; the
     * stored key is kept. Members of a missing clan are ignored.
     */
    public List<ClanData> loadClans(Function<String, ClanName> names) throws SQLException {
        return call(c -> {
            Map<UUID, ClanData> clans = new LinkedHashMap<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT uuid, key, name, flag, description, leader, created_at, friendly_fire, points, max_members, "
                            + "last_rename, icon, owned_icons, minh_huy FROM clans ORDER BY created_at")) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString(1));
                    ClanName name = names.apply(rs.getString(3)).withKey(rs.getString(2));
                    ClanData clan = new ClanData(uuid, name, rs.getString(5), rs.getString(4),
                            UUID.fromString(rs.getString(6)), rs.getLong(7), rs.getInt(8) != 0, rs.getLong(9),
                            rs.getInt(10), rs.getLong(11));
                    clan.icon(rs.getString(12));
                    String owned = rs.getString(13);
                    if (owned != null) {
                        for (String id : owned.split(",")) if (!id.isBlank()) clan.ownedIcons().add(id.trim());
                    }
                    clan.minhHuy(rs.getLong(14));
                    clans.put(uuid, clan);
                }
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT player, clan, role, joined_at, contribution, last_seen FROM members ORDER BY joined_at")) {
                while (rs.next()) {
                    ClanData clan = clans.get(UUID.fromString(rs.getString(2)));
                    if (clan == null) continue;
                    ClanRole role = ClanRole.byKey(rs.getString(3)).orElse(ClanRole.MEMBER);
                    ClanData.Member member = new ClanData.Member(UUID.fromString(rs.getString(1)), role,
                            rs.getLong(4), rs.getLong(5));
                    member.lastSeen(rs.getLong(6));
                    clan.loadMember(member);
                }
            }
            return new ArrayList<>(clans.values());
        });
    }

    public void saveClan(ClanData clan) {
        ClanRow r = ClanRow.of(clan);
        write("save clan " + r.key() + " (" + r.uuid() + ")", c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO clans (uuid, key, name, flag, description, leader, created_at, friendly_fire, points,
                                       max_members, last_rename, icon, owned_icons, minh_huy)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET key = excluded.key, name = excluded.name, flag = excluded.flag,
                        description = excluded.description, leader = excluded.leader,
                        friendly_fire = excluded.friendly_fire, points = excluded.points,
                        max_members = excluded.max_members, last_rename = excluded.last_rename,
                        icon = excluded.icon, owned_icons = excluded.owned_icons,
                        minh_huy = excluded.minh_huy""")) {
                ps.setString(1, r.uuid().toString());
                ps.setString(2, r.key());
                ps.setString(3, r.name());
                ps.setString(4, r.flag());
                ps.setString(5, r.description());
                ps.setString(6, r.leader().toString());
                ps.setLong(7, r.createdAt());
                ps.setInt(8, r.friendlyFire() ? 1 : 0);
                ps.setLong(9, r.points());
                ps.setInt(10, r.maxMembers());
                ps.setLong(11, r.lastRename());
                ps.setString(12, r.icon());
                ps.setString(13, r.ownedIcons());
                ps.setLong(14, r.minhHuy());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Only the points column (frequent change). */
    public void savePoints(UUID clan, long points) {
        write("points of clan " + clan + " = " + points, c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE clans SET points = ? WHERE uuid = ?")) {
                ps.setLong(1, points);
                ps.setString(2, clan.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Only the Minh Huy column. */
    public void saveMinhHuy(UUID clan, long balance) {
        write("minh huy of clan " + clan + " = " + balance, c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE clans SET minh_huy = ? WHERE uuid = ?")) {
                ps.setLong(1, balance);
                ps.setString(2, clan.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Deletes a clan, its member rows, history, storage and purchase counters (the points log is kept). */
    public void deleteClan(UUID clan) {
        write("delete clan " + clan, c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM members WHERE clan = ?")) {
                ps.setString(1, clan.toString());
                ps.executeUpdate();
            }
            for (String table : List.of("clan_history", "vault_items", "shop_purchases")) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE clan = ?")) {
                    ps.setString(1, clan.toString());
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM clans WHERE uuid = ?")) {
                ps.setString(1, clan.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void saveMember(UUID clan, ClanData.Member m) {
        MemberRow r = new MemberRow(m.player(), clan, m.role(), m.joinedAt(), m.contribution(), m.lastSeen());
        write("save member " + r.player() + " of " + clan, c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO members (player, clan, role, joined_at, contribution, last_seen) VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(player) DO UPDATE SET clan = excluded.clan, role = excluded.role,
                        joined_at = excluded.joined_at, contribution = excluded.contribution,
                        last_seen = excluded.last_seen""")) {
                ps.setString(1, r.player().toString());
                ps.setString(2, r.clan().toString());
                ps.setString(3, r.role().key());
                ps.setLong(4, r.joinedAt());
                ps.setLong(5, r.contribution());
                ps.setLong(6, r.lastSeen());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void deleteMember(UUID player) {
        write("delete member " + player, c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM members WHERE player = ?")) {
                ps.setString(1, player.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ notices

    public void addNotice(UUID player, String message, long at) {
        write("notice for " + player, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO notices (player, message, created_at) VALUES (?, ?, ?)")) {
                ps.setString(1, player.toString());
                ps.setString(2, message);
                ps.setLong(3, at);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Deletes notices created before {@code before} (epoch ms): their player has not come back since. */
    public void pruneNotices(long before) {
        write("prune notices", c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM notices WHERE created_at < ?")) {
                ps.setLong(1, before);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Reads and deletes a player's notices, oldest first. */
    public CompletableFuture<List<String>> takeNotices(UUID player) {
        return submit(c -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT message FROM notices WHERE player = ? ORDER BY id")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(rs.getString(1));
                }
            }
            if (!out.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM notices WHERE player = ?")) {
                    ps.setString(1, player.toString());
                    ps.executeUpdate();
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------ points (§5)

    /** Appends one {@code points_log} row; {@code player} may be null (clan-wide award). */
    public void log(long at, UUID clan, UUID player, String source, long delta, String by) {
        write("points log " + clan + " " + source + " " + delta + " by " + by, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO points_log (at, clan, player, source, delta, by) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, at);
                ps.setString(2, clan.toString());
                ps.setString(3, player == null ? null : player.toString());
                ps.setString(4, source);
                ps.setLong(5, delta);
                ps.setString(6, by);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Stores a daily counter (absolute value). */
    public void saveDaily(String owner, String source, String day, long total) {
        write("daily " + owner + " " + source + " " + day + " = " + total, c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO points_daily (owner, source, day, total) VALUES (?, ?, ?, ?)
                    ON CONFLICT(owner, source, day) DO UPDATE SET total = excluded.total""")) {
                ps.setString(1, owner);
                ps.setString(2, source);
                ps.setString(3, day);
                ps.setLong(4, total);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Counters of one day: key {@code owner|source} → total. Deletes older days. */
    public Map<String, Long> loadDaily(String day) throws SQLException {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM points_daily WHERE day <> ?")) {
                ps.setString(1, day);
                ps.executeUpdate();
            }
            Map<String, Long> out = new java.util.HashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT owner, source, total FROM points_daily WHERE day = ?")) {
                ps.setString(1, day);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.put(rs.getString(1) + "|" + rs.getString(2), rs.getLong(3));
                }
            }
            return out;
        });
    }

    /** One page of a clan's log, newest first, plus the total row count. */
    public record LogPage(List<LogRow> rows, int total) {}

    public CompletableFuture<LogPage> logPage(UUID clan, int offset, int limit) {
        return submit(c -> {
            int total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM points_log WHERE clan = ?")) {
                ps.setString(1, clan.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    total = rs.next() ? rs.getInt(1) : 0;
                }
            }
            List<LogRow> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, at, clan, player, source, delta, by FROM points_log WHERE clan = ? "
                            + "ORDER BY at DESC, id DESC LIMIT ? OFFSET ?")) {
                ps.setString(1, clan.toString());
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String player = rs.getString(4);
                        rows.add(new LogRow(rs.getLong(1), rs.getLong(2), UUID.fromString(rs.getString(3)),
                                player == null ? null : UUID.fromString(player), rs.getString(5), rs.getLong(6),
                                rs.getString(7)));
                    }
                }
            }
            return new LogPage(rows, total);
        });
    }

    /** Deletes log rows older than {@code before} (epoch ms). */
    public void pruneLog(long before) {
        write("prune points_log", c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM points_log WHERE at < ?")) {
                ps.setLong(1, before);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ clan history (BOXCLANS_SHOP_SPEC.md §6)

    public void addHistory(HistoryRow r) {
        write("history " + r.type() + " of " + r.clan(), c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO clan_history (clan, at, type, actor, actor_name, target, target_name, amount, detail) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, r.clan().toString());
                ps.setLong(2, r.at());
                ps.setString(3, r.type());
                ps.setString(4, r.actor() == null ? null : r.actor().toString());
                ps.setString(5, r.actorName() == null ? "" : r.actorName());
                ps.setString(6, r.target() == null ? null : r.target().toString());
                ps.setString(7, r.targetName() == null ? "" : r.targetName());
                ps.setLong(8, r.amount());
                ps.setString(9, r.detail() == null ? "" : r.detail());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * One page of a clan's history, newest first.
     *
     * @param types stored type names to keep, empty = every type
     */
    public CompletableFuture<HistoryPage> historyPage(UUID clan, java.util.Collection<String> types, int offset,
                                                      int limit) {
        List<String> filter = List.copyOf(types);
        String where = "clan = ?" + (filter.isEmpty() ? "" : " AND type IN ("
                + String.join(", ", java.util.Collections.nCopies(filter.size(), "?")) + ")");
        return submit(c -> {
            int total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM clan_history WHERE " + where)) {
                bindHistory(ps, clan, filter);
                try (ResultSet rs = ps.executeQuery()) {
                    total = rs.next() ? rs.getInt(1) : 0;
                }
            }
            List<HistoryRow> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT id, at, type, actor, actor_name, target, target_name, "
                    + "amount, detail FROM clan_history WHERE " + where + " ORDER BY at DESC, id DESC LIMIT ? OFFSET ?")) {
                int i = bindHistory(ps, clan, filter);
                ps.setInt(i, limit);
                ps.setInt(i + 1, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String actor = rs.getString(4);
                        String target = rs.getString(6);
                        rows.add(new HistoryRow(rs.getLong(1), clan, rs.getLong(2), rs.getString(3),
                                actor == null ? null : UUID.fromString(actor), rs.getString(5),
                                target == null ? null : UUID.fromString(target), rs.getString(7), rs.getLong(8),
                                rs.getString(9)));
                    }
                }
            }
            return new HistoryPage(rows, total);
        });
    }

    /** Binds the clan and the type filter. @return the next parameter index */
    private static int bindHistory(PreparedStatement ps, UUID clan, List<String> types) throws SQLException {
        ps.setString(1, clan.toString());
        int i = 2;
        for (String t : types) ps.setString(i++, t);
        return i;
    }

    /** Deletes history rows older than {@code before} (epoch ms). */
    public void pruneHistory(long before) {
        write("prune clan_history", c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM clan_history WHERE at < ?")) {
                ps.setLong(1, before);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ shop (BOXCLANS_SHOP_SPEC.md §4)

    public List<ShopRow> loadShopEntries() throws SQLException {
        return call(c -> {
            List<ShopRow> out = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT id, category, item, mode, commands, price, weekly_limit, ord, hidden FROM shop_entries")) {
                while (rs.next()) {
                    out.add(new ShopRow(rs.getInt(1), rs.getString(2), rs.getBytes(3), rs.getString(4), rs.getString(5),
                            rs.getLong(6), rs.getInt(7), rs.getInt(8), rs.getInt(9) != 0));
                }
            }
            return out;
        });
    }

    public void saveShopEntry(ShopRow r) {
        write("save shop entry " + r.id(), c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO shop_entries (id, category, item, mode, commands, price, weekly_limit, ord, hidden)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET category = excluded.category, item = excluded.item,
                        mode = excluded.mode, commands = excluded.commands, price = excluded.price,
                        weekly_limit = excluded.weekly_limit, ord = excluded.ord, hidden = excluded.hidden""")) {
                ps.setInt(1, r.id());
                ps.setString(2, r.category());
                ps.setBytes(3, r.item());
                ps.setString(4, r.mode());
                ps.setString(5, r.commands());
                ps.setLong(6, r.price());
                ps.setInt(7, r.weeklyLimit());
                ps.setInt(8, r.order());
                ps.setInt(9, r.hidden() ? 1 : 0);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void deleteShopEntry(int id) {
        write("delete shop entry " + id, c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM shop_entries WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM shop_purchases WHERE entry = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Purchase counters of one week, key {@code clan|entry}; older weeks than {@code keepFrom} are deleted. */
    public Map<String, Integer> loadPurchases(long week, long keepFrom) throws SQLException {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM shop_purchases WHERE week < ?")) {
                ps.setLong(1, keepFrom);
                ps.executeUpdate();
            }
            Map<String, Integer> out = new java.util.HashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT clan, entry, count FROM shop_purchases WHERE week = ?")) {
                ps.setLong(1, week);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.put(rs.getString(1) + "|" + rs.getInt(2), rs.getInt(3));
                }
            }
            return out;
        });
    }

    public void savePurchases(UUID clan, int entry, long week, int count) {
        write("purchases " + clan + " " + entry + " = " + count, c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO shop_purchases (clan, entry, week, count) VALUES (?, ?, ?, ?)
                    ON CONFLICT(clan, entry, week) DO UPDATE SET count = excluded.count""")) {
                ps.setString(1, clan.toString());
                ps.setInt(2, entry);
                ps.setLong(3, week);
                ps.setInt(4, count);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ clan storage (§5)

    public List<VaultRow> loadVault() throws SQLException {
        return call(c -> {
            List<VaultRow> out = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT id, clan, category, mode, item, commands, amount, created_at FROM vault_items ORDER BY id")) {
                while (rs.next()) {
                    out.add(new VaultRow(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3),
                            rs.getString(4), rs.getBytes(5), rs.getString(6), rs.getInt(7), rs.getLong(8)));
                }
            }
            return out;
        });
    }

    public void saveVaultItem(VaultRow r) {
        write("save vault item " + r.id() + " of " + r.clan(), c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO vault_items (id, clan, category, mode, item, commands, amount, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET category = excluded.category, amount = excluded.amount""")) {
                ps.setLong(1, r.id());
                ps.setString(2, r.clan().toString());
                ps.setString(3, r.category());
                ps.setString(4, r.mode());
                ps.setBytes(5, r.item());
                ps.setString(6, r.commands());
                ps.setInt(7, r.amount());
                ps.setLong(8, r.createdAt());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void deleteVaultItem(long id) {
        write("delete vault item " + id, c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM vault_items WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ player settings

    public CompletableFuture<PlayerSettings> loadSettings(UUID player) {
        return submit(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT chat_sound, member_sound, chat_mode FROM player_settings WHERE player = ?")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return PlayerSettings.DEFAULT;
                    return new PlayerSettings(rs.getInt(1) != 0, rs.getInt(2) != 0, rs.getInt(3) != 0);
                }
            }
        });
    }

    public void saveSettings(UUID player, PlayerSettings s) {
        write("settings of " + player, c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO player_settings (player, chat_sound, member_sound, chat_mode) VALUES (?, ?, ?, ?)
                    ON CONFLICT(player) DO UPDATE SET chat_sound = excluded.chat_sound,
                        member_sound = excluded.member_sound, chat_mode = excluded.chat_mode""")) {
                ps.setString(1, player.toString());
                ps.setInt(2, s.chatSound() ? 1 : 0);
                ps.setInt(3, s.memberSound() ? 1 : 0);
                ps.setInt(4, s.chatMode() ? 1 : 0);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
