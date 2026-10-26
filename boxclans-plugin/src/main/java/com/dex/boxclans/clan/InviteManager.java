package com.dex.boxclans.clan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Pending invites, in memory only (§1, §11). An invite expires after {@code expiry} seconds; a clan has at most
 * {@code maxPending} at once. Main thread only. Expired invites are dropped lazily on every access.
 */
public final class InviteManager {

    /** One invite of {@code target} into {@code clan}, sent by {@code inviter}. */
    public record Invite(UUID clan, UUID target, UUID inviter, long sentAt, long expiresAt) {}

    private final LongSupplier clock;
    /** clan → (target → invite), insertion order = send order. */
    private final Map<UUID, LinkedHashMap<UUID, Invite>> byClan = new HashMap<>();
    private long expiryMillis;
    private int maxPending;

    public InviteManager(LongSupplier clock, long expirySeconds, int maxPending) {
        this.clock = clock;
        configure(expirySeconds, maxPending);
    }

    public void configure(long expirySeconds, int maxPending) {
        this.expiryMillis = expirySeconds * 1000L;
        this.maxPending = maxPending;
    }

    public int maxPending() {
        return maxPending;
    }

    private void purge() {
        long now = clock.getAsLong();
        Iterator<LinkedHashMap<UUID, Invite>> it = byClan.values().iterator();
        while (it.hasNext()) {
            LinkedHashMap<UUID, Invite> invites = it.next();
            invites.values().removeIf(i -> i.expiresAt() <= now);
            if (invites.isEmpty()) it.remove();
        }
    }

    public boolean has(UUID clan, UUID target) {
        purge();
        Map<UUID, Invite> invites = byClan.get(clan);
        return invites != null && invites.containsKey(target);
    }

    public Invite get(UUID clan, UUID target) {
        purge();
        Map<UUID, Invite> invites = byClan.get(clan);
        return invites == null ? null : invites.get(target);
    }

    public int pending(UUID clan) {
        purge();
        Map<UUID, Invite> invites = byClan.get(clan);
        return invites == null ? 0 : invites.size();
    }

    public boolean isFull(UUID clan) {
        return pending(clan) >= maxPending;
    }

    public Invite add(UUID clan, UUID target, UUID inviter) {
        long now = clock.getAsLong();
        Invite invite = new Invite(clan, target, inviter, now, now + expiryMillis);
        byClan.computeIfAbsent(clan, k -> new LinkedHashMap<>()).put(target, invite);
        return invite;
    }

    public Invite remove(UUID clan, UUID target) {
        purge();
        Map<UUID, Invite> invites = byClan.get(clan);
        if (invites == null) return null;
        Invite removed = invites.remove(target);
        if (invites.isEmpty()) byClan.remove(clan);
        return removed;
    }

    /** Invites sent by a clan, oldest first. */
    public List<Invite> ofClan(UUID clan) {
        purge();
        Map<UUID, Invite> invites = byClan.get(clan);
        return invites == null ? List.of() : List.copyOf(invites.values());
    }

    /** Invites received by a player, oldest first. */
    public List<Invite> forPlayer(UUID target) {
        purge();
        List<Invite> out = new ArrayList<>();
        for (Map<UUID, Invite> invites : byClan.values()) {
            Invite i = invites.get(target);
            if (i != null) out.add(i);
        }
        out.sort((a, b) -> Long.compare(a.sentAt(), b.sentAt()));
        return out;
    }

    public void clearClan(UUID clan) {
        byClan.remove(clan);
    }

    public void clearPlayer(UUID target) {
        Iterator<LinkedHashMap<UUID, Invite>> it = byClan.values().iterator();
        while (it.hasNext()) {
            LinkedHashMap<UUID, Invite> invites = it.next();
            invites.remove(target);
            if (invites.isEmpty()) it.remove();
        }
    }

    public void clear() {
        byClan.clear();
    }
}
