package com.dex.boxclans.clan;

import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.api.event.ClanRenameEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenameTest {

    private static final long COOLDOWN = 1800;

    @TempDir
    File dir;
    TestEnv env;
    ClanManager m;
    UUID leader;
    ClanData clan;

    @BeforeEach
    void setUp() {
        env = new TestEnv(dir, 1);
        m = env.manager;
        leader = env.player();
        clan = m.create(leader, TestEnv.name("&cRồng Đỏ")).clan();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    @Test
    void colourOnlyChangeHasNoCooldown() {
        assertEquals(Outcome.OK, m.rename(leader, TestEnv.name("&x&F&F&0&0&0&0Rồng Đỏ"), COOLDOWN, false));
        assertEquals(Outcome.OK, m.rename(leader, TestEnv.name("&aRồng Đỏ"), COOLDOWN, false));
        assertEquals(Outcome.OK, m.rename(leader, TestEnv.name("&bRong Do"), COOLDOWN, false), "same key");
        assertEquals(0, clan.lastRename());
        List<ClanRenameEvent> events = env.eventsOf(ClanRenameEvent.class);
        assertEquals(3, events.size());
        assertTrue(events.get(0).colorOnly());
        assertEquals("RongDo", events.get(0).oldKey());
    }

    @Test
    void textChangeStartsTheCooldown() {
        assertEquals(Outcome.OK, m.rename(leader, TestEnv.name("&4Hắc Long"), COOLDOWN, false));
        assertEquals("HacLong", clan.key());
        assertFalse(env.eventsOf(ClanRenameEvent.class).get(0).colorOnly());
        // the old key is free, the new one is indexed
        assertNull(m.byKey("RongDo"));
        assertNull(env.index.byKey("RongDo"));
        assertEquals(clan, m.byKey("haclong"));
        assertEquals(clan.uuid(), env.index.byKey("HacLong").uuid());
        assertEquals(Outcome.OK, m.create(env.player(), TestEnv.name("Rồng Đỏ")).outcome());

        assertEquals(Outcome.RENAME_COOLDOWN, m.rename(leader, TestEnv.name("Bạch Hổ"), COOLDOWN, false));
        assertEquals(COOLDOWN, m.renameCooldownLeft(clan, COOLDOWN));
        assertEquals(Outcome.OK, m.rename(leader, TestEnv.name("<#FF0000>Hắc Long"),
                COOLDOWN, false), "colours can still change during the cooldown");
        assertEquals(Outcome.OK, m.rename(leader, TestEnv.name("Bạch Hổ"), COOLDOWN, true), "bypass permission");

        env.clock.addAndGet(COOLDOWN * 1000);
        assertEquals(0, m.renameCooldownLeft(clan, COOLDOWN));
        assertEquals(Outcome.OK, m.rename(leader, TestEnv.name("Thanh Long"), COOLDOWN, false));
    }

    @Test
    void renameRules() {
        UUID vice = env.player();
        m.invite(leader, vice);
        m.accept(vice, clan.uuid());
        m.setRole(leader, vice, ClanRole.VICE, false);
        assertEquals(Outcome.NO_PERMISSION, m.rename(vice, TestEnv.name("Khac"), COOLDOWN, false),
                "Phó Bang has no RENAME_CLAN by default");
        assertEquals(Outcome.SAME_NAME, m.rename(leader, TestEnv.name("&cRồng Đỏ"), COOLDOWN, false));
        m.create(env.player(), TestEnv.name("Hắc Long"));
        assertEquals(Outcome.NAME_TAKEN, m.rename(leader, TestEnv.name("&eHac Long"), COOLDOWN, false));
        assertEquals(Outcome.OK, m.adminRename(clan, TestEnv.name("Bach Ho")));
    }

    @Test
    void renamedClanKeepsEverything() throws Exception {
        UUID member = env.player();
        m.invite(leader, member);
        m.accept(member, clan.uuid());
        clan.points(70);
        env.db.saveClan(clan);
        m.rename(leader, TestEnv.name("&6Hắc Long"), COOLDOWN, false);
        env.reload();
        ClanData loaded = env.manager.clan(clan.uuid());
        assertEquals("HacLong", loaded.key());
        assertEquals("&6Hắc Long", loaded.name().raw());
        assertEquals(70, loaded.points());
        assertTrue(loaded.isMember(member));
        assertTrue(loaded.lastRename() > 0);
    }
}
