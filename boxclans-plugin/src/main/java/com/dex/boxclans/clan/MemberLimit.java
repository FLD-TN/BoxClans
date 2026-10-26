package com.dex.boxclans.clan;

import com.dex.boxclans.config.Perks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Member limit from the leader's permissions (§6). The permissions are read only while the leader is online
 * (create, leader join, invite, transfer); the result is stored on the clan for when the leader is offline.
 */
public final class MemberLimit {

    /** Permission check of an online player, {@code null} when the player is offline. */
    @FunctionalInterface
    public interface PermissionLookup {
        Predicate<String> of(UUID player);
    }

    /** A permission check that can also list the permissions the player has (for {@code boxclans.members.<n>}). */
    public interface Granted extends Predicate<String> {
        Collection<String> all();
    }

    private final PermissionLookup lookup;
    private volatile Perks perks;

    public MemberLimit(PermissionLookup lookup, Perks perks) {
        this.lookup = lookup;
        this.perks = perks;
    }

    public void perks(Perks perks) {
        this.perks = perks;
    }

    public Perks perks() {
        return perks;
    }

    /** Limit for a leader, empty when the leader is offline. {@code -1} = unlimited. */
    public OptionalInt of(UUID leader) {
        Predicate<String> has = lookup.of(leader);
        if (has == null) return OptionalInt.empty();
        int limit = perks.memberLimit(has);
        if (limit >= 0 && has instanceof Granted granted) {
            limit = Math.max(limit, Perks.numericLimit(granted.all(), perks.numericPrefix()));
        }
        return OptionalInt.of(limit);
    }

    /** Members above the limit, 0 when unlimited. */
    public static int excess(int size, int limit) {
        return limit < 0 ? 0 : Math.max(0, size - limit);
    }

    /** {@code count} random players from {@code candidates} (all of them when fewer). */
    public static List<UUID> pickRandom(Collection<UUID> candidates, int count, Random random) {
        List<UUID> pool = new ArrayList<>(candidates);
        Collections.shuffle(pool, random);
        return List.copyOf(pool.subList(0, Math.min(count, pool.size())));
    }
}
