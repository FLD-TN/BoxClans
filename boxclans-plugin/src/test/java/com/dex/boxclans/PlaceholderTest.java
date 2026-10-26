package com.dex.boxclans;

import com.dex.boxclans.FakeServer.FakePlayer;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.hook.PlaceholderValues;
import com.dex.boxclans.points.KillProcessor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P8: every placeholder group of §9. */
class PlaceholderTest {

    @TempDir
    File dir;
    FakeServer s;
    PlaceholderValues v;
    FakePlayer a;
    FakePlayer b;
    FakePlayer loner;
    ClanData rong;
    ClanData hac;

    @BeforeEach
    void setUp() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("placeholders.none-value", "&7-");
        s = new FakeServer(dir, cfg);
        v = new PlaceholderValues(s.plugin);
        a = s.join("Alice");
        b = s.join("Bob");
        loner = s.join("Eve");
        s.clan(a, "create");
        s.type(a, "&x&F&F&4&1&2&7Rồng Đỏ");
        a.click('C');
        rong = s.manager.of(a.id);
        s.clan(a, "invite", "Bob");
        s.clan(b, "accept");
        FakePlayer d = s.join("Dan");
        s.clan(d, "create");
        s.type(d, "&bHắc Long");
        d.click('C');
        hac = s.manager.of(d.id);
        s.points.adjust(hac, 50, "admin", "t", true);
    }

    @AfterEach
    void tearDown() {
        s.close();
    }

    @Test
    void renderedTextsAreCachedPerConfiguration() {
        assertEquals("§7-", v.resolve(loner.id, "name"));
        assertEquals("§7-", v.resolve(loner.id, "points"), "same value from the cache");
        assertEquals("§a", v.relational(a.id, b.id, "color"));
        assertEquals("§c", v.relational(a.id, loner.id, "color"));

        // /clanadmin reload installs a new Settings object: the cache starts again with the new values
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("placeholders.none-value", "&8x");
        cfg.set("placeholders.relation-colors.same", "&b");
        org.mockito.Mockito.when(s.plugin.settings()).thenReturn(com.dex.boxclans.config.Settings.from(cfg));
        assertEquals("§8x", v.resolve(loner.id, "name"));
        assertEquals("§b", v.relational(a.id, b.id, "color"));
    }

    @Test
    void ownClan() {
        assertEquals("true", v.resolve(a.id, "has_clan"));
        String name = v.resolve(a.id, "name");
        assertTrue(name.startsWith("§x§f§f§4§1§2§7R"), "hex name as §x: " + name);
        assertEquals("Rồng Đỏ", v.resolve(a.id, "name_plain"));
        assertEquals("RongDo", v.resolve(a.id, "key"));
        assertTrue(name.endsWith("§r"), "the style never runs into what follows: " + name);
        assertEquals(name + " ", v.resolve(a.id, "name_prefix"));
        assertEquals("0", v.resolve(a.id, "points"));
        assertEquals("2", v.resolve(a.id, "rank"));
        assertEquals("2", v.resolve(a.id, "members"));
        assertEquals("2", v.resolve(a.id, "members_online"));
        assertEquals("8", v.resolve(a.id, "max_members"));
        assertEquals("Alice", v.resolve(b.id, "leader"));
        assertEquals("§7-", v.resolve(a.id, "vice"), "none-value when no Phó Bang");
        s.clan(a, "promote", "Bob");
        assertEquals("Bob", v.resolve(a.id, "vice"));
        assertEquals("§aTắt", v.resolve(a.id, "pvp"));
        s.quit(b);
        assertEquals("1", v.resolve(a.id, "members_online"));
    }

    @Test
    void roleAndContribution() {
        assertEquals("§cMinh Chủ", v.resolve(a.id, "role"));
        assertEquals("Minh Chủ", v.resolve(a.id, "role_plain"));
        assertEquals("MinhChu", v.resolve(a.id, "role_key"));
        assertEquals("§c", v.resolve(a.id, "role_color"));
        assertEquals("§aThành Viên", v.resolve(b.id, "role"));
        s.admin(s.console, "points", "give", "Bob", "quest", "12", "-s");
        assertEquals("12", v.resolve(b.id, "contribution"));
        assertEquals("12", v.resolve(a.id, "points"));
    }

    @Test
    void noClanGivesNoneValue() {
        assertEquals("false", v.resolve(loner.id, "has_clan"));
        assertEquals("", v.resolve(loner.id, "name_prefix"), "empty prefix for chat / TAB");
        assertEquals("§7-", v.resolve(loner.id, "name"));
        assertEquals("§7-", v.resolve(loner.id, "role"));
        assertEquals("§7-", v.resolve(loner.id, "contribution"));
        assertNull(v.resolve(loner.id, "nonsense"));
    }

    @Test
    void killCapCounters() {
        for (int i = 0; i < 3; i++) {
            s.kills.handle(new KillProcessor.Kill(a.id, "1.0.0." + i, false, s.join("V" + i).id, "2.0.0." + i, false));
        }
        assertEquals("30", v.resolve(a.id, "kill_points_today"));
        assertEquals("500", v.resolve(a.id, "kill_points_cap"));
    }

    @Test
    void leaderboardAndByKey() {
        assertEquals(hac.name().legacy() + "§r", v.resolve(null, "top_1_name"));
        assertEquals("Hắc Long", v.resolve(null, "top_1_name_plain"));
        assertEquals("HacLong", v.resolve(null, "top_1_key"));
        assertEquals("50", v.resolve(null, "top_1_points"));
        assertEquals("Dan", v.resolve(null, "top_1_leader"));
        assertEquals("1", v.resolve(null, "top_1_members"));
        assertEquals("RongDo", v.resolve(null, "top_2_key"));
        assertEquals("§7-", v.resolve(null, "top_3_name"), "no 3rd clan");

        assertEquals("50", v.resolve(null, "clan_HacLong_points"));
        assertEquals("50", v.resolve(null, "clan_haclong_points"), "key is case-insensitive");
        assertEquals("1", v.resolve(null, "clan_HacLong_rank"));
        assertEquals("2", v.resolve(null, "clan_RongDo_members"));
        assertEquals("Alice", v.resolve(null, "clan_RongDo_leader"));
        assertEquals(rong.name().legacy() + "§r", v.resolve(null, "clan_RongDo_name"));
        assertEquals("§7-", v.resolve(null, "clan_Nope_points"));
    }

    @Test
    void killEventAndRelational() {
        assertEquals("", v.resolve(null, "kill_event"));
        s.killEvents.start("loan-chien", Duration.ofMinutes(5));
        assertEquals("§x§f§f§4§1§2§7§lLOẠN CHIẾN", v.resolve(null, "kill_event"));

        assertEquals("same", v.relational(a.id, b.id, "relation"));
        assertEquals("none", v.relational(a.id, loner.id, "relation"));
        assertEquals("§a", v.relational(a.id, b.id, "color"));
        assertEquals("§c", v.relational(a.id, loner.id, "color"));
        assertEquals("same", v.relational(a.id, a.id, "relation"));
        assertNull(v.relational(a.id, UUID.randomUUID(), "nonsense"));
    }
}
