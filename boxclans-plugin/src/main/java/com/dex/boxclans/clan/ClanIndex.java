package com.dex.boxclans.clan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe read side of the clans (§3 "cam kết luồng"): immutable {@link ClanSnapshot}s in concurrent maps.
 * Written only by {@link ClanManager} on the main thread (after every change); read from any thread.
 */
public final class ClanIndex {

    /** Ranking order: points (highest first), then the oldest clan, then the key. */
    public static final Comparator<ClanSnapshot> RANKING = Comparator.comparingLong(ClanSnapshot::points).reversed()
            .thenComparingLong(ClanSnapshot::createdAt)
            .thenComparing(ClanSnapshot::key, String.CASE_INSENSITIVE_ORDER);

    private record Ranking(long version, List<ClanSnapshot> clans) {}

    private final ConcurrentHashMap<UUID, ClanSnapshot> byUuid = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> byKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> byPlayer = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();
    private volatile Ranking ranking = new Ranking(-1, List.of());

    /** Publishes the current state of a clan. Main thread. */
    public ClanSnapshot publish(ClanData data) {
        ClanSnapshot snap = ClanSnapshot.of(data);
        ClanSnapshot old = byUuid.put(snap.uuid(), snap);
        if (old != null) {
            if (!old.key().equalsIgnoreCase(snap.key())) byKey.remove(lower(old.key()), old.uuid());
            for (UUID p : old.members()) {
                if (!snap.members().contains(p)) byPlayer.remove(p, old.uuid());
            }
        }
        byKey.put(lower(snap.key()), snap.uuid());
        for (UUID p : snap.members()) byPlayer.put(p, snap.uuid());
        version.incrementAndGet();
        return snap;
    }

    /** Removes a clan. Main thread. Returns the last snapshot, or null. */
    public ClanSnapshot remove(UUID clan) {
        ClanSnapshot old = byUuid.remove(clan);
        if (old != null) {
            byKey.remove(lower(old.key()), clan);
            for (UUID p : old.members()) byPlayer.remove(p, clan);
            version.incrementAndGet();
        }
        return old;
    }

    public void clear() {
        byUuid.clear();
        byKey.clear();
        byPlayer.clear();
        version.incrementAndGet();
    }

    // ------------------------------------------------------------------ reads (any thread)

    public ClanSnapshot get(UUID clan) {
        return clan == null ? null : byUuid.get(clan);
    }

    public ClanSnapshot byKey(String key) {
        if (key == null) return null;
        UUID id = byKey.get(lower(key));
        return id == null ? null : byUuid.get(id);
    }

    public ClanSnapshot of(UUID player) {
        if (player == null) return null;
        UUID id = byPlayer.get(player);
        if (id == null) return null;
        ClanSnapshot snap = byUuid.get(id);
        // between two map updates the player map may lag one change behind: trust the snapshot
        return snap != null && snap.members().contains(player) ? snap : null;
    }

    public Collection<ClanSnapshot> all() {
        return byUuid.values();
    }

    public int size() {
        return byUuid.size();
    }

    /** Every clan in ranking order (cached until the next change). */
    public List<ClanSnapshot> ranked() {
        long v = version.get();
        Ranking r = ranking;
        if (r.version() == v) return r.clans();
        List<ClanSnapshot> list = new ArrayList<>(byUuid.values());
        list.sort(RANKING);
        List<ClanSnapshot> clans = List.copyOf(list);
        ranking = new Ranking(v, clans);
        return clans;
    }

    /** 1-based rank, 0 if the clan does not exist. */
    public int rank(UUID clan) {
        List<ClanSnapshot> ranked = ranked();
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).uuid().equals(clan)) return i + 1;
        }
        return 0;
    }

    private static String lower(String key) {
        return key.toLowerCase(Locale.ROOT);
    }
}
