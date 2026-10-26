package com.dex.boxclans;

import com.dex.boxclans.FakeServer.FakePlayer;
import com.dex.boxclans.api.PointResult;
import com.dex.boxclans.api.event.ClanKillEventEndEvent;
import com.dex.boxclans.api.event.ClanKillEventStartEvent;
import com.dex.boxclans.chat.ChatPrompt;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.listener.CombatListener;
import com.dex.boxclans.points.KillProcessor;
import com.dex.boxclans.points.KillProcessor.Kill;
import com.dex.boxclans.points.KillRules;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P6: friendly fire, §5.2 table, the 500 cap and the kill event started by command. */
class CombatTest {

    @TempDir
    File dir;
    FakeServer s;
    FakePlayer a;
    FakePlayer a2;
    FakePlayer b;
    FakePlayer loner;
    ClanData alpha;
    ClanData beta;
    int ip = 10;

    @BeforeEach
    void setUp() {
        s = new FakeServer(dir);
        a = s.join("Alice");
        a2 = s.join("Anna");
        b = s.join("Bob");
        loner = s.join("Eve");
        alpha = create(a, "Alpha");
        s.clan(a, "invite", "Anna");
        s.clan(a2, "accept");
        beta = create(b, "Beta");
    }

    @AfterEach
    void tearDown() {
        s.close();
    }

    private ClanData create(FakePlayer p, String name) {
        s.clan(p, "create");
        assertEquals(ChatPrompt.Result.DONE, s.type(p, name));
        p.click('C');
        return s.manager.of(p.id);
    }

    private String nextIp() {
        return "10.0.0." + (ip++);
    }

    private KillProcessor.Outcome kill(FakePlayer killer, FakePlayer victim) {
        return s.kills.handle(new Kill(killer.id, nextIp(), false, victim.id, nextIp(), false));
    }

    @Test
    void friendlyFireBlockedWithTheRightMessage() {
        CombatListener listener = new CombatListener(s.plugin);
        EntityDamageByEntityEvent e = mock(EntityDamageByEntityEvent.class);
        when(e.getEntity()).thenReturn(a2.player);
        when(e.getDamager()).thenReturn(a.player);
        final boolean[] cancelled = {false};
        org.mockito.Mockito.doAnswer(inv -> {
            cancelled[0] = inv.getArgument(0);
            return null;
        }).when(e).setCancelled(org.mockito.ArgumentMatchers.anyBoolean());
        listener.onDamage(e);
        assertTrue(cancelled[0], "same clan, PvP off: cancelled");
        assertTrue(a.got("Bạn không thể tấn công thành viên cùng bang hội! PvP trong bang đang tắt, bật trong Cài đặt bang hội"),
                a.messages.toString());

        // a Thành Viên shooting an arrow gets the "ask the leader" version
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(a2.player);
        EntityDamageByEntityEvent e2 = mock(EntityDamageByEntityEvent.class);
        when(e2.getEntity()).thenReturn(a.player);
        when(e2.getDamager()).thenReturn(arrow);
        cancelled[0] = false;
        org.mockito.Mockito.doAnswer(inv -> {
            cancelled[0] = inv.getArgument(0);
            return null;
        }).when(e2).setCancelled(org.mockito.ArgumentMatchers.anyBoolean());
        listener.onDamage(e2);
        assertTrue(cancelled[0]);
        assertTrue(a2.got("hãy nhờ Minh Chủ hoặc Phó Bang"), a2.messages.toString());

        // one warning per 3 s
        int before = a.messages.size();
        listener.onDamage(e);
        assertEquals(before, a.messages.size());

        // other clan: not touched; friendly fire on: not touched
        EntityDamageByEntityEvent e3 = mock(EntityDamageByEntityEvent.class);
        when(e3.getEntity()).thenReturn(b.player);
        when(e3.getDamager()).thenReturn(a.player);
        listener.onDamage(e3);
        org.mockito.Mockito.verify(e3, org.mockito.Mockito.never()).setCancelled(true);
        s.clan(a, "pvp");
        EntityDamageByEntityEvent e4 = mock(EntityDamageByEntityEvent.class);
        when(e4.getEntity()).thenReturn(a2.player);
        when(e4.getDamager()).thenReturn(a.player);
        listener.onDamage(e4);
        org.mockito.Mockito.verify(e4, org.mockito.Mockito.never()).setCancelled(true);
    }

    @Test
    void friendlyFireCoversCausedDamageAndHarmfulPotions() {
        PotionEffect poison = mock(PotionEffect.class);
        PotionEffect healing = mock(PotionEffect.class);
        CombatListener listener = new CombatListener(s.plugin, effect -> effect == poison);

        // TNT lit by Alice (or a crystal she broke) hurting Anna: the damage source names Alice
        DamageSource source = mock(DamageSource.class);
        when(source.getCausingEntity()).thenReturn(a.player);
        EntityDamageByEntityEvent tnt = mock(EntityDamageByEntityEvent.class);
        when(tnt.getEntity()).thenReturn(a2.player);
        when(tnt.getDamager()).thenReturn(mock(TNTPrimed.class));
        when(tnt.getDamageSource()).thenReturn(source);
        listener.onDamage(tnt);
        verify(tnt).setCancelled(true);
        // the same TNT hurting Bob (other clan): untouched
        EntityDamageByEntityEvent tntBob = mock(EntityDamageByEntityEvent.class);
        when(tntBob.getEntity()).thenReturn(b.player);
        when(tntBob.getDamager()).thenReturn(mock(TNTPrimed.class));
        when(tntBob.getDamageSource()).thenReturn(source);
        listener.onDamage(tntBob);
        verify(tntBob, never()).setCancelled(true);

        // harmful splash: Anna gets none of it; Bob and the thrower are untouched
        ThrownPotion potion = mock(ThrownPotion.class);
        when(potion.getShooter()).thenReturn(a.player);
        when(potion.getEffects()).thenReturn(List.of(poison));
        PotionSplashEvent splash = mock(PotionSplashEvent.class);
        when(splash.getPotion()).thenReturn(potion);
        when(splash.getAffectedEntities()).thenReturn(List.of(a2.player, b.player, a.player));
        listener.onSplash(splash);
        verify(splash).setIntensity(a2.player, 0);
        verify(splash, never()).setIntensity(eq(b.player), anyDouble());
        verify(splash, never()).setIntensity(eq(a.player), anyDouble());

        // healing splash: clan mates still get it
        ThrownPotion heal = mock(ThrownPotion.class);
        when(heal.getShooter()).thenReturn(a.player);
        when(heal.getEffects()).thenReturn(List.of(healing));
        PotionSplashEvent healSplash = mock(PotionSplashEvent.class);
        when(healSplash.getPotion()).thenReturn(heal);
        when(healSplash.getAffectedEntities()).thenReturn(List.of(a2.player));
        listener.onSplash(healSplash);
        verify(healSplash, never()).setIntensity(any(), anyDouble());

        // harmful lingering cloud: Anna is left out, Bob stays
        AreaEffectCloud cloud = mock(AreaEffectCloud.class);
        when(cloud.getSource()).thenReturn(a.player);
        when(cloud.getCustomEffects()).thenReturn(List.of(poison));
        List<LivingEntity> affected = new ArrayList<>(List.of(a2.player, b.player));
        AreaEffectCloudApplyEvent apply = mock(AreaEffectCloudApplyEvent.class);
        when(apply.getEntity()).thenReturn(cloud);
        when(apply.getAffectedEntities()).thenReturn(affected);
        listener.onCloud(apply);
        assertEquals(List.of(b.player), affected);

        // PvP on in the clan: nothing is blocked any more
        s.clan(a, "pvp");
        PotionSplashEvent splash2 = mock(PotionSplashEvent.class);
        when(splash2.getPotion()).thenReturn(potion);
        when(splash2.getAffectedEntities()).thenReturn(List.of(a2.player));
        listener.onSplash(splash2);
        verify(splash2, never()).setIntensity(any(), anyDouble());
    }

    @Test
    void clanlessKillerIsNeverCheckedLoggedOrReported() {
        FakePlayer loner2 = s.join("Zoe");
        // both clanless, same IP: nothing at all
        assertEquals(KillProcessor.Outcome.Kind.NONE,
                s.kills.handle(new Kill(loner.id, "9.9.9.9", false, loner2.id, "9.9.9.9", false)).kind());
        // clanless killer, clan victim, same IP: no rule A
        KillProcessor.Outcome o = s.kills.handle(new Kill(loner.id, "9.9.9.9", false, b.id, "9.9.9.9", false));
        assertEquals(KillRules.Verdict.ALLOW, o.verdict());
        assertNull(o.killer());
        // ... nor rule C: the pair is not even counted
        for (int i = 0; i < 5; i++) kill(loner, b);
        assertEquals(0, s.kills.rules().pairKills(loner.id, b.id));
        assertTrue(s.feedback.lines.isEmpty(), "no warning, staff alert or antialt.log line: " + s.feedback.lines);
        assertEquals(0, beta.points());

        // a killer WITH a clan is still checked against a clanless victim
        KillProcessor.Outcome blocked = s.kills.handle(new Kill(a.id, "6.6.6.6", false, loner.id, "6.6.6.6", false));
        assertEquals(KillRules.Verdict.SAME_IP, blocked.verdict());
    }

    @Test
    void normalKillTable() {
        // other clan: +10, victim loses nothing
        KillProcessor.Outcome o = kill(a, b);
        assertEquals(10, o.killer().amount());
        assertEquals(PointResult.Status.ZERO, o.victim().status());
        assertEquals(10, alpha.points());
        assertEquals(0, beta.points());
        // clanless victim: +10
        assertEquals(10, kill(a, loner).killer().amount());
        // clanless killer: nothing, victim loses nothing
        KillProcessor.Outcome o2 = kill(loner, b);
        assertNull(o2.killer());
        assertEquals(0, beta.points());
        // same clan, even with PvP on
        s.clan(a, "pvp");
        assertEquals(KillProcessor.Outcome.Kind.SAME_CLAN, kill(a, a2).kind());
        assertEquals(20, alpha.points());
        // self / environment
        assertEquals(KillProcessor.Outcome.Kind.NONE, s.kills.handle(new Kill(a.id, "1", false, a.id, "1", false)).kind());
        // rule A: no points for anyone, warning, staff alert, log
        KillProcessor.Outcome same = s.kills.handle(new Kill(a.id, "7.7.7.7", false, b.id, "7.7.7.7", false));
        assertEquals(KillRules.Verdict.SAME_IP, same.verdict());
        assertEquals(20, alpha.points());
        assertEquals(List.of("blocked SAME_IP", "staff SAME_IP", "log SAME_IP"), s.feedback.lines);
    }

    @Test
    void clanCapOf500BlocksAndBroadcastsOnce() {
        List<FakePlayer> victims = new java.util.ArrayList<>();
        for (int i = 0; i < 52; i++) victims.add(s.join("V" + i));
        for (int i = 0; i < 50; i++) assertEquals(10, kill(i % 2 == 0 ? a : a2, victims.get(i)).killer().amount());
        assertEquals(500, alpha.points());
        assertEquals(500, s.points.clanToday(alpha.uuid(), "kill"));
        long broadcasts = a2.messages.stream().filter(m -> m.contains("Bang hội đã đạt tối đa 500 Danh Vọng từ Hạ gục người chơi hôm nay")).count();
        assertEquals(1, broadcasts, "broadcast once when the cap is reached");

        KillProcessor.Outcome capped = kill(a, victims.get(50));
        assertEquals(PointResult.Status.CLAN_DAILY_CAP, capped.killer().status());
        kill(a2, victims.get(51));
        assertEquals(500, alpha.points());
        assertEquals(2, s.feedback.count("cap 500"), "the killer is told every time");
        broadcasts = a2.messages.stream().filter(m -> m.contains("Bang hội đã đạt tối đa 500 Danh Vọng")).count();
        assertEquals(1, broadcasts, "still only once");
        // another clan is not capped
        assertEquals(10, kill(b, victims.get(0)).killer().amount());
    }

    @Test
    void killEventByCommand() {
        // 49 kills before the event: 490/500
        List<FakePlayer> victims = new java.util.ArrayList<>();
        for (int i = 0; i < 70; i++) victims.add(s.join("V" + i));
        for (int i = 0; i < 49; i++) kill(a, victims.get(i));
        assertEquals(490, alpha.points());

        s.admin(s.console, "killevent", "start", "loan-chien", "30m");
        assertTrue(FakeServer.CONSOLE.stream().anyMatch(m -> m.contains("Đã bắt đầu sự kiện LOẠN CHIẾN")), FakeServer.CONSOLE.toString());
        assertEquals("loan-chien", s.plugin.killEvents().active().orElseThrow());
        assertEquals(1, FakeServer.EVENTS.stream().filter(e -> e instanceof ClanKillEventStartEvent).count());
        s.admin(s.console, "killevent", "start", "loan-chien", "30m");
        assertTrue(FakeServer.CONSOLE.stream().anyMatch(m -> m.contains("Đang có sự kiện")));

        // no limits: 20 kills -> +200, the daily counter stays at 490
        for (int i = 49; i < 69; i++) assertEquals(10, kill(a, victims.get(i)).killer().amount());
        assertEquals(690, alpha.points());
        assertEquals(490, s.points.clanToday(alpha.uuid(), "kill"));
        // rule C ignored: the same victim 5 times
        for (int i = 0; i < 5; i++) assertEquals(10, kill(a, b).killer().amount());
        // death costs 5 (Beta had 0 points: allow-negative false keeps it at 0)
        assertEquals(0, beta.points());
        s.admin(s.console, "points", "add", "Beta", "100", "-s");
        KillProcessor.Outcome death = kill(a, b);
        assertEquals(-5, death.victim().amount());
        assertEquals(95, beta.points());
        // rules A and B still block
        KillProcessor.Outcome sameIp = s.kills.handle(new Kill(a.id, "8.8.8.8", false, b.id, "8.8.8.8", false));
        assertEquals(KillRules.Verdict.SAME_IP, sameIp.verdict());
        assertEquals(95, beta.points(), "blocked: the victim loses nothing either");
        s.kills.handle(new Kill(a.id, "1.1.1.1", false, victims.get(69).id, "4.4.4.4", false));
        KillProcessor.Outcome network = s.kills.handle(new Kill(a.id, "1.1.1.1", false, loner.id, "4.4.4.4", false));
        assertEquals(KillRules.Verdict.NETWORK, network.verdict());

        s.admin(s.console, "killevent", "stop");
        assertTrue(s.plugin.killEvents().active().isEmpty());
        assertEquals(ClanKillEventEndEvent.Reason.STOPPED, FakeServer.EVENTS.stream()
                .filter(e -> e instanceof ClanKillEventEndEvent).map(e -> ((ClanKillEventEndEvent) e).reason())
                .findFirst().orElseThrow());
        // after the event: back to 490/500, 10 points left today
        long before = alpha.points();
        assertEquals(10, kill(a2, s.join("Late1")).killer().amount());
        assertEquals(PointResult.Status.CLAN_DAILY_CAP, kill(a2, s.join("Late2")).killer().status());
        assertEquals(before + 10, alpha.points());
        assertFalse(s.plugin.killEvents().active().isPresent());
    }
}
