package com.dex.boxclans.points;

import com.dex.boxclans.config.Settings;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Anti kill-farm rules (§7), RAM only. Checked in order:
 * <ul>
 *   <li><b>A</b> same IP: killer and victim share an IP.</li>
 *   <li><b>B</b> one network, many accounts: within {@code network-window}, the killer was already credited for
 *       killing <i>another</i> account with the victim's IP (the first kill counts, the next accounts do not).</li>
 *   <li><b>C</b> per pair: the first kill opens a {@code pair-window}; the first {@code pair-max-kills} kills in it
 *       count, the next ones do not; the next kill after the window opens a new one. Skipped during a kill event
 *       that ignores it, and such kills do not move the window.</li>
 * </ul>
 * The clan daily cap is the fourth rule, applied by {@link PointsService}. Main thread only.
 */
public final class KillRules {

    /** Result of a check. */
    public enum Verdict {
        ALLOW,
        /** Rule A. */
        SAME_IP,
        /** Rule B. */
        NETWORK,
        /** Rule C. */
        PAIR_LIMIT;

        /** A or B: a likely alt account (staff is told). */
        public boolean altSign() {
            return this == SAME_IP || this == NETWORK;
        }
    }

    private record Credited(UUID victim, String ip, long at) {}

    private static final class Window {
        long start;
        int kills;
    }

    private volatile Settings.AntiFarm cfg;
    /** killer → credited kills (oldest first), for rule B. */
    private final Map<UUID, Deque<Credited>> credited = new HashMap<>();
    /** killer|victim → window, for rule C. */
    private final Map<String, Window> windows = new HashMap<>();

    public KillRules(Settings.AntiFarm cfg) {
        this.cfg = cfg;
    }

    public void configure(Settings.AntiFarm cfg) {
        this.cfg = cfg;
    }

    /**
     * Checks one player kill.
     *
     * @param ipRulesBypassed {@code boxclans.antialt.bypass}: rules A and B are skipped
     * @param ignorePair      a kill event that ignores rule C
     */
    public Verdict check(UUID killer, String killerIp, UUID victim, String victimIp, long now,
                         boolean ipRulesBypassed, boolean ignorePair) {
        Settings.AntiFarm c = cfg;
        if (!ipRulesBypassed) {
            if (c.sameIp() && killerIp != null && killerIp.equals(victimIp)) return Verdict.SAME_IP;
            if (c.networkWindow() > 0 && victimIp != null) {
                Deque<Credited> list = credited.get(killer);
                if (list != null) {
                    prune(list, now - c.networkWindow() * 1000L);
                    for (Credited k : list) {
                        if (victimIp.equals(k.ip()) && !k.victim().equals(victim)) return Verdict.NETWORK;
                    }
                }
            }
        }
        if (!ignorePair && c.pairWindow() > 0) {
            Window w = windows.computeIfAbsent(killer + "|" + victim, k -> new Window());
            if (w.kills == 0 || now - w.start >= c.pairWindow() * 1000L) {
                w.start = now;
                w.kills = 0;
            }
            w.kills++;
            if (w.kills > c.pairMaxKills()) return Verdict.PAIR_LIMIT;
        }
        return Verdict.ALLOW;
    }

    /** Remembers that the killer was credited for this victim (rule B). */
    public void credited(UUID killer, UUID victim, String victimIp, long now) {
        if (victimIp == null) return;
        Deque<Credited> list = credited.computeIfAbsent(killer, k -> new ArrayDeque<>());
        list.addLast(new Credited(victim, victimIp, now));
        while (list.size() > 64) list.removeFirst();
    }

    private static void prune(Deque<Credited> list, long before) {
        while (!list.isEmpty() && list.peekFirst().at() < before) list.removeFirst();
    }

    /** Drops expired state (called every few minutes). */
    public void cleanup(long now) {
        Settings.AntiFarm c = cfg;
        long b = now - c.networkWindow() * 1000L;
        Iterator<Deque<Credited>> it = credited.values().iterator();
        while (it.hasNext()) {
            Deque<Credited> list = it.next();
            prune(list, b);
            if (list.isEmpty()) it.remove();
        }
        long w = c.pairWindow() * 1000L;
        windows.values().removeIf(x -> now - x.start >= w);
    }

    /** Kills counted in the current window of a pair (tests, diagnostics). */
    public int pairKills(UUID killer, UUID victim) {
        Window w = windows.get(killer + "|" + victim);
        return w == null ? 0 : w.kills;
    }
}
