package com.dex.boxclans.clan;

import com.dex.boxclans.api.ClanRelation;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.api.ClanServiceImpl;
import com.dex.boxclans.api.event.ClanCreateEvent;
import com.dex.boxclans.api.event.ClanDisbandEvent;
import com.dex.boxclans.api.event.ClanLeaderChangeEvent;
import com.dex.boxclans.api.event.ClanMemberJoinEvent;
import com.dex.boxclans.api.event.ClanMemberLeaveEvent;
import com.dex.boxclans.api.event.ClanRoleChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanManagerTest {

    @TempDir
    File dir;
    TestEnv env;
    ClanManager m;

    @BeforeEach
    void setUp() {
        env = new TestEnv(dir, 42);
        m = env.manager;
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private List<UUID> players(int n) {
        List<UUID> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(env.player());
        return out;
    }

    @Test
    void createRegistersClanAndRejectsSameKey() {
        UUID leader = env.player();
        ClanManager.CreateResult r = m.create(leader, TestEnv.name("&c&lRồng &6Đỏ"));
        assertEquals(Outcome.OK, r.outcome());
        ClanData clan = r.clan();
        assertEquals("RongDo", clan.key());
        assertEquals(ClanRole.LEADER, clan.roleOf(leader));
        assertEquals(8, clan.maxMembers(), "default-max-members");
        assertEquals(1, env.eventsOf(ClanCreateEvent.class).size());

        // same key with other colours / no accents
        assertEquals(Outcome.NAME_TAKEN, m.create(env.player(), TestEnv.name("&aRong Do")).outcome());
        assertEquals(Outcome.ALREADY_IN_CLAN, m.create(leader, TestEnv.name("Khac")).outcome());

        ClanSnapshot snap = env.index.of(leader);
        assertNotNull(snap);
        assertEquals("Rồng Đỏ", snap.plainName());
        assertEquals(clan.uuid(), env.index.byKey("rongdo").uuid());
    }

    @Test
    void leaderPermissionSetsTheLimit() {
        UUID leader = env.player("boxclans.members.vip", "boxclans.members.mvp");
        assertEquals(16, m.create(leader, TestEnv.name("Ice")).clan().maxMembers());
        UUID unlimited = env.player("boxclans.members.unlimited", "boxclans.members.vip");
        assertEquals(-1, m.create(unlimited, TestEnv.name("Fire")).clan().maxMembers());
    }

    @Test
    void numericPermissionSetsTheLimit() {
        UUID a = env.player("boxclans.members.20");
        assertEquals(20, m.create(a, TestEnv.name("Num")).clan().maxMembers());
        UUID b = env.player("boxclans.members.15", "boxclans.members.30", "boxclans.members.vip");
        assertEquals(30, m.create(b, TestEnv.name("Many")).clan().maxMembers(), "largest wins, named tiers still work");
        UUID c = env.player("boxclans.members.5");
        assertEquals(8, m.create(c, TestEnv.name("Low")).clan().maxMembers(), "never below the default");
        UUID d = env.player("boxclans.members.unlimited", "boxclans.members.99");
        assertEquals(-1, m.create(d, TestEnv.name("All")).clan().maxMembers());
        UUID e = env.player("boxclans.members.abc", "boxclans.members.");
        assertEquals(8, m.create(e, TestEnv.name("Junk")).clan().maxMembers());
    }

    @Test
    void inviteAcceptDenyAndExpiry() {
        UUID leader = env.player();
        UUID a = env.player();
        UUID b = env.player();
        ClanData clan = m.create(leader, TestEnv.name("Alpha")).clan();

        assertEquals(Outcome.OK, m.invite(leader, a));
        assertEquals(Outcome.ALREADY_INVITED, m.invite(leader, a));
        assertEquals(Outcome.SELF, m.invite(leader, leader));
        assertEquals(Outcome.NOT_IN_CLAN, m.invite(a, b));
        assertEquals(Outcome.NO_INVITE, m.accept(b, clan.uuid()));

        assertEquals(Outcome.OK, m.accept(a, clan.uuid()));
        assertEquals(ClanRole.MEMBER, clan.roleOf(a));
        assertEquals(1, env.eventsOf(ClanMemberJoinEvent.class).size());
        assertEquals(Outcome.ALREADY_IN_CLAN, m.accept(a, clan.uuid()));
        // a Thành Viên cannot invite
        assertEquals(Outcome.NO_PERMISSION, m.invite(a, b));

        assertEquals(Outcome.OK, m.invite(leader, b));
        assertEquals(Outcome.OK, m.deny(b, clan.uuid()));
        assertEquals(Outcome.NO_INVITE, m.accept(b, clan.uuid()));

        assertEquals(Outcome.OK, m.invite(leader, b));
        env.clock.addAndGet(301_000);
        assertEquals(Outcome.NO_INVITE, m.accept(b, clan.uuid()), "invites expire after 300 s");

        assertEquals(Outcome.OK, m.invite(leader, b));
        assertEquals(Outcome.OK, m.uninvite(leader, b));
        assertEquals(Outcome.NO_INVITE, m.accept(b, clan.uuid()));

        // invited player in another clan meanwhile
        UUID c = env.player();
        assertEquals(Outcome.OK, m.invite(leader, c));
        m.create(c, TestEnv.name("Beta"));
        assertEquals(Outcome.ALREADY_IN_CLAN, m.accept(c, clan.uuid()));
        assertEquals(Outcome.TARGET_IN_CLAN, m.invite(leader, c));
    }

    @Test
    void inviteLimitAndFullClan() {
        UUID leader = env.player();
        ClanData clan = m.create(leader, TestEnv.name("Alpha")).clan();
        for (int i = 0; i < 10; i++) assertEquals(Outcome.OK, m.invite(leader, env.player()));
        assertEquals(Outcome.INVITE_LIMIT, m.invite(leader, env.player()), "10 pending invites at most");

        UUID leader2 = env.player();
        ClanData full = env.clanWith(leader2, "Gamma", players(7));
        assertEquals(8, full.size());
        assertEquals(Outcome.CLAN_FULL, m.invite(leader2, env.player()));
        assertNotNull(clan);
    }

    @Test
    void acceptRechecksTheLimit() {
        UUID leader = env.player();
        ClanData clan = env.clanWith(leader, "Alpha", players(6));
        UUID late = env.player();
        UUID last = env.player();
        assertEquals(Outcome.OK, m.invite(leader, late));
        assertEquals(Outcome.OK, m.invite(leader, last));
        assertEquals(Outcome.OK, m.accept(last, clan.uuid()));
        assertEquals(Outcome.CLAN_FULL, m.accept(late, clan.uuid()));
    }

    @Test
    void kickRules() {
        UUID leader = env.player();
        List<UUID> p = players(4);
        ClanData clan = env.clanWith(leader, "Alpha", p);
        UUID vice = p.get(0);
        assertEquals(Outcome.OK, m.setRole(leader, vice, ClanRole.VICE, false).outcome());

        assertEquals(Outcome.NO_PERMISSION, m.kick(p.get(1), p.get(2)), "Thành Viên cannot kick");
        assertEquals(Outcome.TARGET_IS_LEADER, m.kick(vice, leader));
        assertEquals(Outcome.SELF, m.kick(vice, vice));
        assertEquals(Outcome.OK, m.kick(vice, p.get(1)), "Phó Bang kicks Thành Viên");
        assertFalse(clan.isMember(p.get(1)));
        assertNull(env.index.of(p.get(1)));
        assertEquals("notice-kicked", env.notices.get(env.notices.size() - 1).key());
        assertEquals(Outcome.TARGET_NOT_MEMBER, m.kick(vice, env.player()));
        assertEquals(Outcome.OK, m.kick(leader, vice), "Minh Chủ kicks Phó Bang");

        ClanMemberLeaveEvent last = env.eventsOf(ClanMemberLeaveEvent.class).get(1);
        assertEquals(ClanMemberLeaveEvent.Reason.KICK, last.reason());
        assertEquals(vice, last.player());
    }

    @Test
    void adminKickRules() {
        UUID leader = env.player();
        List<UUID> p = players(2);
        env.clanWith(leader, "Alpha", p);
        m.setRole(leader, p.get(0), ClanRole.VICE, false);
        assertEquals(Outcome.TARGET_IS_LEADER, m.adminKick(leader));
        assertEquals(Outcome.OK, m.adminKick(p.get(0)));
    }

    @Test
    void leaveRules() {
        UUID leader = env.player();
        List<UUID> p = players(1);
        ClanData clan = env.clanWith(leader, "Alpha", p);
        assertEquals(Outcome.LEADER_CANNOT_LEAVE, m.leave(leader));
        assertEquals(Outcome.OK, m.leave(p.get(0)));
        assertFalse(clan.isMember(p.get(0)));
        assertEquals(Outcome.NOT_IN_CLAN, m.leave(p.get(0)));
        assertEquals(ClanMemberLeaveEvent.Reason.LEAVE, env.eventsOf(ClanMemberLeaveEvent.class).get(0).reason());
    }

    @Test
    void setRolePromoteDemoteAndSwapVice() {
        UUID leader = env.player();
        List<UUID> p = players(3);
        ClanData clan = env.clanWith(leader, "Alpha", p);
        UUID a = p.get(0);
        UUID b = p.get(1);

        assertEquals(Outcome.NOT_LEADER, m.setRole(a, b, ClanRole.VICE, false).outcome());
        assertEquals(Outcome.SELF, m.setRole(leader, leader, ClanRole.MEMBER, false).outcome());
        assertEquals(Outcome.SAME_ROLE, m.setRole(leader, a, ClanRole.MEMBER, false).outcome());

        assertEquals(Outcome.OK, m.setRole(leader, a, ClanRole.VICE, false).outcome());
        assertEquals(ClanRole.VICE, clan.roleOf(a));

        // a second Phó Bang needs the swap confirmation (max 1)
        ClanManager.RoleResult needs = m.setRole(leader, b, ClanRole.VICE, false);
        assertEquals(Outcome.NEEDS_CONFIRM, needs.outcome());
        assertEquals(a, needs.displaced());
        assertEquals(ClanRole.VICE, clan.roleOf(a), "nothing changes before the confirmation");
        assertEquals(ClanRole.MEMBER, clan.roleOf(b));

        ClanManager.RoleResult swap = m.setRole(leader, b, ClanRole.VICE, true);
        assertEquals(Outcome.OK, swap.outcome());
        assertEquals(a, swap.displaced());
        assertEquals(ClanRole.MEMBER, clan.roleOf(a));
        assertEquals(ClanRole.VICE, clan.roleOf(b));
        assertEquals(1, clan.withRole(ClanRole.VICE).size());
        assertEquals(b, env.index.get(clan.uuid()).vice().orElseThrow());

        assertEquals(Outcome.OK, m.setRole(leader, b, ClanRole.MEMBER, false).outcome());
        assertEquals(ClanRole.MEMBER, clan.roleOf(b));
        assertTrue(env.index.get(clan.uuid()).vice().isEmpty());

        List<ClanRoleChangeEvent> changes = env.eventsOf(ClanRoleChangeEvent.class);
        assertEquals(4, changes.size());
        assertEquals(ClanRole.VICE, changes.get(1).oldRole()); // a demoted by the swap
        assertEquals(ClanRole.MEMBER, changes.get(1).newRole());
    }

    @Test
    void transferWithoutExcess() {
        UUID leader = env.player();
        List<UUID> p = players(4);
        ClanData clan = env.clanWith(leader, "Alpha", p);
        UUID vice = p.get(0);
        UUID heir = p.get(1);
        m.setRole(leader, vice, ClanRole.VICE, false);

        assertEquals(Outcome.NEEDS_CONFIRM, m.setRole(leader, heir, ClanRole.LEADER, false).outcome());
        assertEquals(leader, clan.leader());

        ClanManager.RoleResult r = m.setRole(leader, heir, ClanRole.LEADER, true);
        assertEquals(Outcome.OK, r.outcome());
        assertTrue(r.kicked().isEmpty());
        assertEquals(heir, clan.leader());
        assertEquals(ClanRole.LEADER, clan.roleOf(heir));
        assertEquals(ClanRole.MEMBER, clan.roleOf(leader), "old Minh Chủ becomes Thành Viên");
        assertEquals(ClanRole.VICE, clan.roleOf(vice), "Phó Bang keeps the role");
        assertEquals(5, clan.size());
        ClanLeaderChangeEvent e = env.eventsOf(ClanLeaderChangeEvent.class).get(0);
        assertEquals(leader, e.oldLeader());
        assertEquals(heir, e.newLeader());
        assertEquals(heir, env.index.get(clan.uuid()).leader());
        assertEquals(Outcome.NOT_LEADER, m.setRole(leader, vice, ClanRole.MEMBER, false).outcome());

        // the Phó Bang receives the clan: no Phó Bang left
        assertEquals(Outcome.OK, m.setRole(heir, vice, ClanRole.LEADER, true).outcome());
        assertTrue(clan.withRole(ClanRole.VICE).isEmpty());
        assertEquals(ClanRole.MEMBER, clan.roleOf(heir));
    }

    @Test
    void transferToLowerLimitKicksRandomlyButNeverTheLeaders() {
        for (long seed = 0; seed < 40; seed++) {
            TestEnv e = new TestEnv(new File(dir, "s" + seed), seed);
            try {
                UUID leader = e.player("boxclans.members.mvp"); // 16
                List<UUID> others = new ArrayList<>();
                for (int i = 0; i < 13; i++) others.add(e.player());
                ClanData clan = e.clanWith(leader, "Alpha", others);
                assertEquals(14, clan.size());
                UUID heir = others.get((int) (seed % 13));
                e.online.put(heir, java.util.Set.of("boxclans.members.vip")); // 12

                ClanManager.TransferPreview preview = e.manager.previewTransfer(clan, heir);
                assertEquals(12, preview.limit());
                assertEquals(2, preview.excess());

                ClanManager.RoleResult r = e.manager.setRole(leader, heir, ClanRole.LEADER, true);
                assertEquals(Outcome.OK, r.outcome());
                assertEquals(2, r.kicked().size());
                assertFalse(r.kicked().contains(leader), "old Minh Chủ is never kicked");
                assertFalse(r.kicked().contains(heir), "new Minh Chủ is never kicked");
                assertEquals(12, clan.size());
                assertEquals(12, clan.maxMembers());
                assertTrue(clan.isMember(leader));
                assertEquals(heir, clan.leader());
                for (UUID k : r.kicked()) {
                    assertNull(e.index.of(k));
                    assertTrue(e.notices.stream().anyMatch(n -> n.player().equals(k)
                            && n.key().equals("notice-kicked-over-limit")));
                }
                long overLimit = e.eventsOf(ClanMemberLeaveEvent.class).stream()
                        .filter(x -> x.reason() == ClanMemberLeaveEvent.Reason.OVER_LIMIT).count();
                assertEquals(2, overLimit);
            } finally {
                e.close();
            }
        }
    }

    @Test
    void offlineHeirKeepsLimitAndTrimSparesLeader() {
        UUID leader = env.player("boxclans.members.vip");
        List<UUID> p = players(9);
        ClanData clan = env.clanWith(leader, "Alpha", p);
        UUID heir = p.get(3);
        env.online.remove(heir);
        ClanManager.RoleResult r = m.setRole(leader, heir, ClanRole.LEADER, true);
        assertEquals(Outcome.OK, r.outcome());
        assertTrue(r.kicked().isEmpty(), "offline heir keeps the current limit (12)");
        assertEquals(12, clan.maxMembers());

        clan.maxMembers(1);
        List<UUID> trimmed = m.trim(clan);
        assertEquals(9, trimmed.size(), "trim kicks everyone but the leader when the limit is 1");
        assertFalse(trimmed.contains(heir));
        assertEquals(1, clan.size());
    }

    @Test
    void disbandRemovesEverything() {
        UUID leader = env.player();
        List<UUID> p = players(3);
        ClanData clan = env.clanWith(leader, "Rồng Đỏ", p);
        m.invite(leader, env.player());
        assertEquals(Outcome.NOT_LEADER, m.disband(p.get(0)));
        assertEquals(Outcome.OK, m.disband(leader));

        assertNull(m.clan(clan.uuid()));
        assertNull(env.index.get(clan.uuid()));
        assertNull(env.index.byKey("RongDo"));
        for (UUID x : p) assertNull(env.index.of(x));
        assertEquals(0, env.invites.pending(clan.uuid()));
        assertEquals(4, env.eventsOf(ClanMemberLeaveEvent.class).stream()
                .filter(e -> e.reason() == ClanMemberLeaveEvent.Reason.DISBAND).count());
        ClanDisbandEvent d = env.eventsOf(ClanDisbandEvent.class).get(0);
        assertEquals(leader, d.actor());
        assertEquals(3, env.notices.stream().filter(n -> n.key().equals("notice-clan-disbanded")).count(),
                "everyone but the leader who disbanded");
        // the key is free again
        assertEquals(Outcome.OK, m.create(p.get(0), TestEnv.name("&aRong Do")).outcome());
    }

    @Test
    void settingsNeedRolePermissions() {
        UUID leader = env.player();
        List<UUID> p = players(2);
        ClanData clan = env.clanWith(leader, "Alpha", p);
        m.setRole(leader, p.get(0), ClanRole.VICE, false);
        assertEquals(Outcome.OK, m.setDescription(p.get(0), "Mô tả"));
        assertEquals(Outcome.NO_PERMISSION, m.setDescription(p.get(1), "x"));
        assertEquals(Outcome.OK, m.setFriendlyFire(p.get(0), true));
        assertTrue(clan.friendlyFire());
        assertTrue(env.index.get(clan.uuid()).friendlyFire());
        assertEquals(Outcome.NO_PERMISSION, m.setFriendlyFire(p.get(1), false));
        assertEquals(Outcome.OK, m.setFlag(leader, "WHITE:RED_CROSS"));
        assertEquals("Mô tả", env.index.get(clan.uuid()).description());
    }

    @Test
    void databaseSavesAndReloads() throws Exception {
        UUID leader = env.player("boxclans.members.mvp");
        List<UUID> p = players(4);
        ClanData clan = env.clanWith(leader, "&c&lRồng &6Đỏ", p);
        m.setRole(leader, p.get(0), ClanRole.VICE, false);
        m.setDescription(leader, "Bang mạnh nhất");
        m.setFriendlyFire(leader, true);
        m.setFlag(leader, "flag-data");
        m.kick(leader, p.get(3));
        clan.points(120);
        clan.member(p.get(1)).contribution(40);
        env.db.saveClan(clan);
        env.db.saveMember(clan.uuid(), clan.member(p.get(1)));

        UUID other = env.player();
        ClanData gone = env.clanWith(other, "Tan Ra", players(1));
        m.disband(other);

        env.reload();
        ClanManager r = env.manager;
        assertEquals(1, r.clans().size());
        ClanData loaded = r.clan(clan.uuid());
        assertNotNull(loaded);
        assertNull(r.clan(gone.uuid()));
        assertEquals("RongDo", loaded.key());
        assertEquals("&c&lRồng &6Đỏ", loaded.name().raw());
        assertEquals("Rồng Đỏ", loaded.name().plain());
        assertEquals("Bang mạnh nhất", loaded.description());
        assertEquals("flag-data", loaded.flag());
        assertTrue(loaded.friendlyFire());
        assertEquals(120, loaded.points());
        assertEquals(16, loaded.maxMembers());
        assertEquals(leader, loaded.leader());
        assertEquals(4, loaded.size());
        assertEquals(ClanRole.LEADER, loaded.roleOf(leader));
        assertEquals(ClanRole.VICE, loaded.roleOf(p.get(0)));
        assertEquals(ClanRole.MEMBER, loaded.roleOf(p.get(1)));
        assertEquals(40, loaded.member(p.get(1)).contribution());
        assertFalse(loaded.isMember(p.get(3)));
        assertEquals(clan.uuid(), env.index.of(p.get(2)).uuid());
        assertEquals(loaded, r.byKey("rongdo"));
    }

    @Test
    void noticesPersistInDatabase() throws Exception {
        UUID p = UUID.randomUUID();
        env.db.addNotice(p, "<red>một", 1);
        env.db.addNotice(p, "<red>hai", 2);
        assertEquals(List.of("<red>một", "<red>hai"), env.db.takeNotices(p).get());
        assertTrue(env.db.takeNotices(p).get().isEmpty(), "notices are deleted once read");
    }

    @Test
    void serviceAnswersRelations() {
        ClanServiceImpl service = new ClanServiceImpl(env.index, () -> env.roles);
        UUID a = env.player();
        UUID b = env.player();
        UUID c = env.player();
        UUID loner = env.player();
        ClanData alpha = env.clanWith(a, "Alpha", List.of(b));
        ClanData beta = env.clanWith(c, "Beta", List.of());
        alpha.points(50);
        beta.points(80);
        m.publish(alpha);
        m.publish(beta);

        assertTrue(service.sameClan(a, b));
        assertTrue(service.isFriendly(a, b));
        assertTrue(service.isFriendly(a, a));
        assertFalse(service.sameClan(a, a));
        assertFalse(service.isFriendly(a, c));
        assertFalse(service.isFriendly(a, loner));
        assertEquals(ClanRelation.SAME_CLAN, service.relation(a, b));
        assertEquals(ClanRelation.SELF, service.relation(a, a));
        assertEquals(ClanRelation.NONE, service.relation(a, loner));
        assertEquals(ClanRole.MEMBER, service.roleOf(b).orElseThrow());
        assertEquals("Minh Chủ", service.roleDisplay(ClanRole.LEADER));
        assertEquals("§c", service.roleColor(ClanRole.LEADER));
        assertEquals(beta.uuid(), service.top(1).get(0).uuid());
        assertEquals(1, service.rank(beta.uuid()));
        assertEquals(2, service.rank(alpha.uuid()));
        assertEquals(0, service.rank(UUID.randomUUID()));
        assertEquals(alpha.uuid(), service.clanByKey("ALPHA").orElseThrow().uuid());
        assertTrue(service.clanOf(loner).isEmpty());
    }
}
