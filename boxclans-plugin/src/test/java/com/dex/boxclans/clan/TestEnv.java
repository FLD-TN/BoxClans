package com.dex.boxclans.clan;

import com.dex.boxclans.config.Perks;
import com.dex.boxclans.config.RoleRegistry;
import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.ClanName;
import com.dex.boxclans.text.ClanNameFormatter;
import org.bukkit.event.Event;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/** A ClanManager on a real SQLite file, with recorded events / notices and fake online permissions. */
public final class TestEnv implements AutoCloseable {

    static final Logger LOG = Logger.getLogger("test");

    record Notice(UUID player, String key, Object[] placeholders) {}

    public final File dbFile;
    public final AtomicLong clock = new AtomicLong(1_000_000L);
    /** Online players and their permissions; absent = offline. */
    public final Map<UUID, Set<String>> online = new HashMap<>();
    public final List<Event> events = new ArrayList<>();
    public final List<Notice> notices = new ArrayList<>();
    public final RoleRegistry roles;
    public final Perks perks;
    public final ClanIndex index = new ClanIndex();
    public final InviteManager invites;
    public final MemberLimit limits;
    public Database db;
    public ClanManager manager;

    public TestEnv(File dir, long seed) {
        this.dbFile = new File(dir, "clans.db");
        this.roles = RoleRegistry.from(YamlFiles.load(new File(dir, "none-roles.yml"),
                getClass().getClassLoader().getResourceAsStream("roles.yml"), LOG), LOG);
        this.perks = Perks.from(YamlFiles.load(new File(dir, "none-perks.yml"),
                getClass().getClassLoader().getResourceAsStream("perks.yml"), LOG));
        this.invites = new InviteManager(clock::get, 300, 10);
        this.limits = new MemberLimit(p -> {
            Set<String> perms = online.get(p);
            if (perms == null) return null;
            return new MemberLimit.Granted() {
                public boolean test(String permission) {
                    return perms.contains(permission);
                }

                public java.util.Collection<String> all() {
                    return perms;
                }
            };
        }, perks);
        this.db = Database.open(dbFile, LOG);
        this.manager = newManager(seed);
    }

    ClanManager newManager(long seed) {
        return new ClanManager(index, db, invites, limits, roles, false, new ClanManager.Events() {
            @Override
            public <T extends Event> T call(T event) {
                events.add(event);
                return event;
            }
        }, (player, key, placeholders) -> notices.add(new Notice(player, key, placeholders)),
                clock::get, new Random(seed));
    }

    /** Closes and reopens the database, then reloads every clan into a fresh manager. */
    public void reload() throws Exception {
        db.close();
        db = Database.open(dbFile, LOG);
        manager = newManager(1);
        manager.load(db.loadClans(ClanNameFormatter::render));
    }

    public static ClanName name(String raw) {
        return ClanNameFormatter.render(raw);
    }

    public UUID player(String... perms) {
        UUID id = UUID.randomUUID();
        online.put(id, Set.of(perms));
        return id;
    }

    /** Creates a clan led by {@code leader} with {@code extra} invited members. */
    public ClanData clanWith(UUID leader, String raw, List<UUID> extra) {
        ClanData clan = manager.create(leader, name(raw)).clan();
        for (UUID p : extra) {
            if (manager.invite(leader, p) != Outcome.OK) throw new IllegalStateException("invite failed");
            if (manager.accept(p, clan.uuid()) != Outcome.OK) throw new IllegalStateException("accept failed");
        }
        return clan;
    }

    /** An event sink that records into {@link #events} and lets {@code hook} change events (e.g. cancel). */
    public ClanManager.Events eventSink(java.util.function.Consumer<Event> hook) {
        return new ClanManager.Events() {
            @Override
            public <T extends Event> T call(T event) {
                events.add(event);
                if (hook != null) hook.accept(event);
                return event;
            }
        };
    }

    public <T extends Event> List<T> eventsOf(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Event e : events) if (type.isInstance(e)) out.add(type.cast(e));
        return out;
    }

    @Override
    public void close() {
        if (db != null) db.close();
    }
}
