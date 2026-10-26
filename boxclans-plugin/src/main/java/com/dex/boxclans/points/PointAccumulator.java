package com.dex.boxclans.points;

import java.util.HashMap;
import java.util.Map;

/**
 * Counts for {@code every: N} (§5.1): points are added on every N-th call only, so a source like
 * {@code mine-block} never writes the database per block. RAM only (a restart starts the counts again).
 */
public final class PointAccumulator {

    private final Map<String, Integer> counts = new HashMap<>();

    /** Counts one call; true (and the count restarts) when it is the N-th. */
    public boolean count(String key, int every) {
        if (every <= 1) return true;
        int n = counts.merge(key, 1, Integer::sum);
        if (n >= every) {
            counts.remove(key);
            return true;
        }
        return false;
    }

    public int pending(String key) {
        return counts.getOrDefault(key, 0);
    }

    /** Drops every count of one owner ({@code p:<uuid>} / {@code c:<uuid>}), e.g. a player leaving the server. */
    public void forget(String owner) {
        String prefix = owner + "|";
        counts.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void clear() {
        counts.clear();
    }
}
