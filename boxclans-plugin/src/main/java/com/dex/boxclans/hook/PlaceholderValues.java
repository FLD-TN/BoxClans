package com.dex.boxclans.hook;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.config.Messages;
import com.dex.boxclans.config.RoleRegistry;
import com.dex.boxclans.config.Settings;
import com.dex.boxclans.points.DailyCounters;
import com.dex.boxclans.points.KillEventManager;
import com.dex.boxclans.points.PointSource;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Names;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Values of the {@code %boxclans_...%} placeholders (§9), independent of PlaceholderAPI so they can be tested.
 * Every value is legacy text ({@code §} + hex). Safe from any thread: reads the {@code ClanIndex} snapshots, the
 * configuration snapshots and {@link DailyCounters#peek} only.
 */
public final class PlaceholderValues {

    /**
     * Legacy text of the configured templates (none-value, pvp-on, relation colours, kill event names...), rendered
     * once per configuration: TAB asks for the same few values thousands of times, and every render parses
     * MiniMessage. {@code /clanadmin reload} swaps the {@link Settings} / {@link Messages} objects, which starts a
     * fresh cache. Any thread: the cache is immutable apart from its concurrent map.
     */
    private record Rendered(Settings settings, Messages messages, Map<String, String> texts) {}

    private final BoxClansPlugin plugin;
    private volatile Rendered rendered = new Rendered(null, null, new ConcurrentHashMap<>());

    public PlaceholderValues(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    /** The cache of the current configuration (a new one after a reload). */
    private Map<String, String> texts() {
        Settings s = plugin.settings();
        Messages m = plugin.messages();
        Rendered r = rendered;
        if (r.settings() != s || r.messages() != m) {
            r = new Rendered(s, m, new ConcurrentHashMap<>());
            rendered = r;
        }
        return r.texts();
    }

    private String none() {
        return texts().computeIfAbsent("none", k -> legacyText(plugin.settings().noneValue()));
    }

    /** A configuration template as legacy text; cached, so only for config values, never for player input. */
    private String cachedLegacy(String template) {
        if (template == null || template.isEmpty()) return "";
        return texts().computeIfAbsent("t:" + template, k -> legacyText(template));
    }

    private static String legacyText(String template) {
        return template == null || template.isEmpty() ? "" : Text.legacy(Text.colorize(template));
    }

    private String messageText(String key) {
        return texts().computeIfAbsent("m:" + key, k -> Text.legacy(Text.parse(plugin.messages().text(key))));
    }

    private String relationColor(boolean same) {
        return texts().computeIfAbsent(same ? "rel:same" : "rel:none", k -> Text.legacyColor(
                same ? plugin.settings().relationColorSame() : plugin.settings().relationColorNone()));
    }

    private String max(ClanSnapshot c) {
        return c.maxMembers() < 0 ? messageText("unlimited") : String.valueOf(c.maxMembers());
    }

    private static int online(ClanSnapshot c) {
        int n = 0;
        for (UUID m : c.members()) if (Bukkit.getPlayer(m) != null) n++;
        return n;
    }

    /** {@code %boxclans_<params>%} for a player ({@code null} player: only the global placeholders). */
    public String resolve(UUID player, String params) {
        if (params == null) return null;
        String p = params.toLowerCase(Locale.ROOT);
        if (p.startsWith("top_")) return top(params.substring(4));
        if (p.startsWith("clan_")) return byKey(params.substring(5));
        if (p.equals("kill_event")) {
            KillEventManager k = plugin.killEvents();
            KillEventManager.Active a = k == null ? null : k.current();
            return a == null ? "" : cachedLegacy(a.profile().display());
        }
        ClanSnapshot clan = player == null ? null : plugin.index().of(player);
        if (p.equals("has_clan")) return String.valueOf(clan != null);
        if (p.equals("name_prefix")) return clan == null ? "" : closed(clan) + " ";
        // shield icon: never the none-value, so a clan without icon leaves no space (BOXCLANS_ICON_SPEC.md §5)
        if (p.equals("icon")) return clan == null ? "" : plugin.iconGlyphs().animated(clan.icon());
        if (p.equals("icon_static")) return clan == null ? "" : plugin.iconGlyphs().still(clan.icon());
        if (p.equals("icon_id")) return clan == null || clan.icon().isEmpty() ? "none" : clan.icon();
        if (clan == null) {
            return switch (p) {
                case "name", "name_plain", "key", "points", "rank", "members", "members_online", "max_members",
                     "leader", "vice", "pvp", "kill_points_today", "kill_points_cap", "role", "role_plain",
                     "role_key", "role_color", "contribution", "minhhuy", "minhhuy_formatted" -> none();
                default -> null;
            };
        }
        ClanRole role = clan.roleOf(player);
        RoleRegistry.RoleInfo info = role == null ? null : plugin.roles().info(role);
        return switch (p) {
            case "name" -> closed(clan);
            case "name_plain" -> clan.plainName();
            case "key" -> clan.key();
            case "points" -> String.valueOf(clan.points());
            case "rank" -> String.valueOf(plugin.index().rank(clan.uuid()));
            case "members" -> String.valueOf(clan.size());
            case "members_online" -> String.valueOf(online(clan));
            case "max_members" -> max(clan);
            case "leader" -> Names.of(clan.leader());
            case "vice" -> clan.vice().map(Names::of).orElse(none());
            case "pvp" -> messageText(clan.friendlyFire() ? "pvp-on" : "pvp-off");
            case "kill_points_today" -> String.valueOf(killToday(clan));
            case "kill_points_cap" -> String.valueOf(killCap());
            case "role" -> info == null ? none() : info.colorLegacy() + info.display();
            case "role_plain" -> info == null ? none() : info.display();
            case "role_key" -> role == null ? none() : role.key();
            case "role_color" -> info == null ? none() : info.colorLegacy();
            case "contribution" -> String.valueOf(clan.contribution(player));
            // Minh Huy: only the player's own clan (never top_<n>_ / clan_<key>_), BOXCLANS_SHOP_SPEC.md §2.4
            case "minhhuy" -> String.valueOf(clan.minhHuy());
            case "minhhuy_formatted" -> com.dex.boxclans.util.Numbers.grouped(clan.minhHuy());
            default -> null;
        };
    }

    /**
     * The coloured name followed by {@code §r}: legacy text cannot close a style, so without the reset the
     * clan's colour and bold would run into whatever follows the placeholder on TAB / scoreboards / holograms.
     */
    private static String closed(ClanSnapshot clan) {
        return clan.name() + "§r";
    }

    private long killToday(ClanSnapshot clan) {
        var points = plugin.points();
        return points == null ? 0 : points.daily().peek(DailyCounters.clan(clan.uuid()), "kill");
    }

    private long killCap() {
        PointSource kill = plugin.pointsConfig() == null ? null : plugin.pointsConfig().source("kill");
        return kill == null ? 0 : kill.clanDailyCap();
    }

    /** {@code top_<n>_<field>}. */
    private String top(String rest) {
        int sep = rest.indexOf('_');
        if (sep <= 0) return null;
        int n;
        try {
            n = Integer.parseInt(rest.substring(0, sep));
        } catch (NumberFormatException ex) {
            return null;
        }
        String field = rest.substring(sep + 1).toLowerCase(Locale.ROOT);
        List<ClanSnapshot> ranked = plugin.index().ranked();
        if (n < 1) return null;
        if (n > ranked.size()) return missing(field);
        return clanField(ranked.get(n - 1), field, n);
    }

    /** {@code clan_<key>_<field>}; keys have no underscore. */
    private String byKey(String rest) {
        int sep = rest.lastIndexOf('_');
        if (sep <= 0) return null;
        String field = rest.substring(sep + 1).toLowerCase(Locale.ROOT);
        ClanSnapshot clan = plugin.index().byKey(rest.substring(0, sep));
        if (clan == null) return missing(field);
        return clanField(clan, field, -1);
    }

    /** Value of a clan field when the clan does not exist: icons stay empty (no space), the rest is none-value. */
    private String missing(String field) {
        if (field.startsWith("icon")) return isClanField(field) ? "" : null;
        return isClanField(field) ? none() : null;
    }

    private static boolean isClanField(String f) {
        return switch (f) {
            case "name", "name_plain", "key", "points", "leader", "members", "rank", "icon", "icon_static" -> true;
            default -> false;
        };
    }

    private String clanField(ClanSnapshot c, String field, int rank) {
        return switch (field) {
            case "name" -> closed(c);
            case "name_plain" -> c.plainName();
            case "key" -> c.key();
            case "points" -> String.valueOf(c.points());
            case "leader" -> Names.of(c.leader());
            case "members" -> String.valueOf(c.size());
            case "rank" -> String.valueOf(rank > 0 ? rank : plugin.index().rank(c.uuid()));
            case "icon" -> plugin.iconGlyphs().animated(c.icon());
            case "icon_static" -> plugin.iconGlyphs().still(c.icon());
            default -> null;
        };
    }

    /** {@code %rel_boxclans_<params>%}: {@code relation} ({@code same} / {@code none}) and {@code color}. */
    public String relational(UUID one, UUID two, String params) {
        if (params == null || one == null || two == null) return null;
        boolean same = one.equals(two) || plugin.service().sameClan(one, two);
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "relation" -> same ? "same" : "none";
            case "color" -> relationColor(same);
            default -> null;
        };
    }
}
