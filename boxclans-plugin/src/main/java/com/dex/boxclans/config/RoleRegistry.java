package com.dex.boxclans.config;

import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.clan.RolePermission;
import com.dex.boxclans.text.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Immutable snapshot of {@code roles.yml} (§4A). */
public final class RoleRegistry {

    public static final String FILE = "roles.yml";

    /**
     * One role.
     *
     * @param display     shown name ({@code Minh Chủ})
     * @param color       colour as written in the file ({@code &c})
     * @param colorMini   colour as an opening MiniMessage tag, to put before text in templates
     * @param colorLegacy colour as legacy {@code §} text ({@code §c}), for the API and placeholders
     * @param description lore lines (MiniMessage)
     * @param max         maximum number of members with this role, -1 = unlimited
     * @param guiColorMini colour of the role in menus ({@code gui-color}, falls back to {@code color}); clan chat
     *                     and placeholders keep {@code color}
     * @param guiStyled    the role name in menus and messages (1.4.3): {@code gui-color} + name, a gradient
     *                     ({@code <gradient:#FFD600:#FF8C00>}) closed after the name
     */
    public record RoleInfo(String display, String color, String colorMini, String colorLegacy,
                           List<String> description, int max, Set<RolePermission> permissions, String guiColorMini,
                           String guiStyled) {}

    /** {@code gui-color} of the Minh Chủ before 1.4.3; a file still holding it gets the gradient. */
    static final String OLD_LEADER_GUI_COLOR = "&#FF5C5C";
    static final String LEADER_GUI_COLOR = "<gradient:#FFD600:#FF8C00>";

    /** {@code gui-color} + name; a {@code <gradient:...>} or {@code <rainbow>} tag is closed after the name. */
    static String styled(String guiColor, String display) {
        String name = Text.MM.escapeTags(display);
        String c = guiColor == null ? "" : guiColor.trim();
        if ((c.startsWith("<gradient") || c.startsWith("<rainbow")) && c.endsWith(">")) {
            int end = c.indexOf(':');
            if (end < 0 || end > c.indexOf('>')) end = c.indexOf('>');
            return c + name + "</" + c.substring(1, end) + ">";
        }
        return Text.legacyToMini(c) + name;
    }

    private final Map<ClanRole, RoleInfo> roles;

    private RoleRegistry(Map<ClanRole, RoleInfo> roles) {
        this.roles = roles;
    }

    public static RoleRegistry from(YamlConfiguration yaml, Logger logger) {
        Map<ClanRole, RoleInfo> roles = new EnumMap<>(ClanRole.class);
        for (ClanRole role : ClanRole.values()) {
            ConfigurationSection s = yaml.getConfigurationSection("roles." + role.key());
            String display = s == null ? role.key() : YamlFiles.string(s, "display", role.key());
            String color = s == null ? "&f" : YamlFiles.string(s, "color", "&f");
            String guiColor = s == null ? color : YamlFiles.string(s, "gui-color", color);
            if (role == ClanRole.LEADER && OLD_LEADER_GUI_COLOR.equalsIgnoreCase(guiColor.trim())) {
                guiColor = LEADER_GUI_COLOR;
                logger.info("[" + FILE + "] MinhChu.gui-color still had the old default " + OLD_LEADER_GUI_COLOR
                        + ", using the 1.4.3 gradient " + LEADER_GUI_COLOR);
            }
            List<String> description = new ArrayList<>();
            if (s != null) for (String line : s.getStringList("description")) description.add(Text.legacyToMini(line));
            Set<RolePermission> perms = EnumSet.noneOf(RolePermission.class);
            if (role == ClanRole.LEADER) {
                perms = EnumSet.allOf(RolePermission.class);
            } else if (s != null) {
                for (String name : s.getStringList("permissions")) {
                    RolePermission.parse(name).ifPresentOrElse(perms::add,
                            () -> logger.warning("[" + FILE + "] Unknown permission '" + name + "' for " + role.key()));
                }
            }
            int max = switch (role) {
                case LEADER -> 1;
                case VICE -> s == null ? 1 : (int) YamlFiles.integer(s, "max", 1);
                case MEMBER -> -1;
            };
            String colorLegacy = colorLegacy(color);
            roles.put(role, new RoleInfo(display, color, Text.legacyToMini(color), colorLegacy,
                    List.copyOf(description), max, Set.copyOf(perms), Text.legacyToMini(guiColor),
                    styled(guiColor, display)));
        }
        return new RoleRegistry(Map.copyOf(roles));
    }

    /** {@code &c} → {@code §c}, {@code &#FF0000} → {@code §x§F§F§0§0§0§0}. */
    static String colorLegacy(String color) {
        return Text.legacyColor(color);
    }

    public RoleInfo info(ClanRole role) {
        return roles.get(role);
    }

    public String display(ClanRole role) {
        return roles.get(role).display();
    }

    /** Whether a role holds a permission (the leader holds all). */
    public boolean has(ClanRole role, RolePermission permission) {
        return role == ClanRole.LEADER || roles.get(role).permissions().contains(permission);
    }

    /** Maximum number of Phó Bang, -1 = unlimited, 0 = role disabled. */
    public int maxVice() {
        return roles.get(ClanRole.VICE).max();
    }
}
