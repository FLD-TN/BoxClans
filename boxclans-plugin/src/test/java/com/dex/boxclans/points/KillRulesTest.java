package com.dex.boxclans.points;

import com.dex.boxclans.config.Settings;
import com.dex.boxclans.points.KillRules.Verdict;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KillRulesTest {

    private static final long MIN = 60_000L;
    private static final Settings.AntiFarm DEFAULTS = new Settings.AntiFarm(true, 600, 600, 3, true, true);

    private final KillRules rules = new KillRules(DEFAULTS);
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final long t2000 = 20 * 60 * MIN;

    private Verdict kill(UUID killer, String killerIp, UUID victim, String victimIp, long at) {
        Verdict v = rules.check(killer, killerIp, victim, victimIp, at, false, false);
        if (v == Verdict.ALLOW) rules.credited(killer, victim, victimIp, at);
        return v;
    }

    /** The table of §7: A kills B five times. */
    @Test
    void ruleCSpecTable() {
        assertEquals(Verdict.ALLOW, kill(a, "1.1.1.1", b, "2.2.2.2", t2000));               // 20:00 opens 20:00-20:10
        assertEquals(Verdict.ALLOW, kill(a, "1.1.1.1", b, "2.2.2.2", t2000 + 3 * MIN));     // 20:03
        assertEquals(Verdict.ALLOW, kill(a, "1.1.1.1", b, "2.2.2.2", t2000 + 5 * MIN));     // 20:05
        assertEquals(Verdict.PAIR_LIMIT, kill(a, "1.1.1.1", b, "2.2.2.2", t2000 + 7 * MIN)); // 20:07
        assertEquals(Verdict.ALLOW, kill(a, "1.1.1.1", b, "2.2.2.2", t2000 + 12 * MIN));    // 20:12 new window
        assertEquals(Verdict.ALLOW, kill(a, "1.1.1.1", b, "2.2.2.2", t2000 + 13 * MIN));
        assertEquals(Verdict.ALLOW, kill(a, "1.1.1.1", b, "2.2.2.2", t2000 + 14 * MIN));
        assertEquals(Verdict.PAIR_LIMIT, kill(a, "1.1.1.1", b, "2.2.2.2", t2000 + 21 * MIN)); // still 20:12-20:22
        assertEquals(Verdict.ALLOW, kill(a, "1.1.1.1", b, "2.2.2.2", t2000 + 22 * MIN));
    }

    @Test
    void ruleCIsPerPairAndNeedsNoIp() {
        UUID c = UUID.randomUUID();
        for (int i = 0; i < 3; i++) assertEquals(Verdict.ALLOW, kill(a, null, b, null, t2000 + i));
        assertEquals(Verdict.PAIR_LIMIT, kill(a, null, b, null, t2000 + 3));
        assertEquals(Verdict.ALLOW, kill(a, null, c, null, t2000 + 4), "another victim");
        assertEquals(Verdict.ALLOW, kill(b, null, a, null, t2000 + 5), "the other direction is another pair");
    }

    @Test
    void ruleCIgnoredDuringEventDoesNotMoveTheWindow() {
        for (int i = 0; i < 3; i++) kill(a, "1", b, "2", t2000 + i);
        for (int i = 0; i < 10; i++) {
            assertEquals(Verdict.ALLOW, rules.check(a, "1", b, "2", t2000 + 10 + i, false, true));
        }
        assertEquals(3, rules.pairKills(a, b));
        assertEquals(Verdict.PAIR_LIMIT, kill(a, "1", b, "2", t2000 + 30));
    }

    @Test
    void ruleASameIp() {
        assertEquals(Verdict.SAME_IP, kill(a, "9.9.9.9", b, "9.9.9.9", t2000));
        assertEquals(Verdict.ALLOW, rules.check(a, "9.9.9.9", b, "9.9.9.9", t2000, true, false), "bypass");
        assertEquals(Verdict.SAME_IP, rules.check(a, "9.9.9.9", b, "9.9.9.9", t2000, false, true),
                "still applies during a kill event");
    }

    /** §7 rule B: kill the main account, then the alts of the same person. */
    @Test
    void ruleBScenario() {
        UUID main = UUID.randomUUID();
        UUID alt1 = UUID.randomUUID();
        UUID alt2 = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        assertEquals(Verdict.ALLOW, kill(a, "1.1.1.1", main, "5.5.5.5", t2000), "the first kill counts");
        assertEquals(Verdict.NETWORK, kill(a, "1.1.1.1", alt1, "5.5.5.5", t2000 + MIN));
        assertEquals(Verdict.NETWORK, kill(a, "1.1.1.1", alt2, "5.5.5.5", t2000 + 2 * MIN));
        assertEquals(Verdict.ALLOW, kill(a, "1.1.1.1", main, "5.5.5.5", t2000 + 3 * MIN),
                "the same account again is rule C's business");
        assertEquals(Verdict.ALLOW, kill(a, "1.1.1.1", stranger, "6.6.6.6", t2000 + 4 * MIN));
        assertEquals(Verdict.NETWORK, rules.check(a, "1.1.1.1", alt1, "5.5.5.5", t2000 + 5 * MIN, false, true),
                "still applies during a kill event");
        // 10 minutes after the last credited kill of that network
        assertEquals(Verdict.ALLOW, kill(a, "1.1.1.1", alt1, "5.5.5.5", t2000 + 14 * MIN));
        UUID other = UUID.randomUUID();
        assertEquals(Verdict.ALLOW, kill(other, "3.3.3.3", alt2, "5.5.5.5", t2000 + 14 * MIN),
                "rule B is per killer");
    }

    @Test
    void rulesCanBeTurnedOff() {
        KillRules off = new KillRules(new Settings.AntiFarm(false, 0, 0, 3, false, false));
        for (int i = 0; i < 10; i++) assertEquals(Verdict.ALLOW, off.check(a, "1", b, "1", t2000 + i, false, false));
    }
}
