package com.dex.boxclans.history;

import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.config.Icons;
import com.dex.boxclans.config.Messages;
import com.dex.boxclans.config.RoleRegistry;
import com.dex.boxclans.shop.ShopActions;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.ClanNameFormatter;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Durations;
import com.dex.boxclans.util.Numbers;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@code clan_history} row into the name and lore of its sheet of paper (BOXCLANS_SHOP_SPEC.md §6.2), from
 * {@code menus/clan-history.yml}: {@code types.<TYPE>.name / lore}, then {@code entry-item.lore-footer}. The values
 * in {@code detail} are split here (see {@link HistoryType}).
 */
public final class HistoryFormat {

    /** Name and lore lines, MiniMessage. */
    public record Sheet(String name, List<String> lore) {}

    private final ConfigurationSection menu;
    private final Messages messages;
    private final RoleRegistry roles;
    private final Icons icons;
    private final DateTimeFormatter time;

    public HistoryFormat(ConfigurationSection menu, Messages messages, RoleRegistry roles, Icons icons, ZoneId zone) {
        this.menu = menu;
        this.messages = messages;
        this.roles = roles;
        this.icons = icons;
        this.time = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone);
    }

    private String text(String path, String fallback) {
        String v = menu == null ? null : menu.getString(path);
        return v == null ? fallback : v;
    }

    private static Text.Mini mini(String legacy) {
        return new Text.Mini(Text.legacyToMini(legacy));
    }

    private String role(String key) {
        ClanRole r = ClanRole.byKey(key).orElse(null);
        if (r == null) return key;
        RoleRegistry.RoleInfo info = roles.info(r);
        return info.guiStyled();
    }

    private String icon(String id) {
        if (id == null || id.isEmpty()) return Text.legacyToMini(text("none", "&#AAAAAAKhông có"));
        Icons.Icon i = icons == null ? null : icons.icon(id);
        return i == null ? Text.MM.escapeTags(id) : Text.legacyToMini(i.display());
    }

    /** A stored clan name (as typed) in its colours, closed so nothing leaks after it. */
    private static String clanName(String raw) {
        return ClanNameFormatter.render(raw == null ? "" : raw).mini();
    }

    private static String part(String[] parts, int i) {
        return i < parts.length ? parts[i] : "";
    }

    public Sheet render(Database.HistoryRow row) {
        HistoryType type = HistoryType.of(row.type());
        String key = type == null ? "UNKNOWN" : type.name();
        String[] parts = row.detail() == null ? new String[0] : row.detail().split(HistoryType.SEPARATOR, -1);
        String actor = row.actorName() == null || row.actorName().isEmpty()
                ? Text.legacyToMini(text("staff", "&#FF55FFQuản trị viên")) : Text.MM.escapeTags(row.actorName());
        List<Object> ph = new ArrayList<>(List.of(
                "actor", new Text.Mini(actor),
                "target", row.targetName() == null ? "" : row.targetName(),
                "amount", Numbers.grouped(row.amount()),
                "detail", row.detail() == null ? "" : row.detail(),
                "time", time.format(Instant.ofEpochMilli(row.at())),
                "type", row.type()));
        if (type != null) {
            switch (type) {
                case CREATE -> ph.addAll(List.of("name", new Text.Mini(clanName(row.detail()))));
                case ROLE -> {
                    String[] roles = row.detail().split(">", 2);
                    ph.addAll(List.of("old", new Text.Mini(role(roles[0])),
                            "new", new Text.Mini(role(roles.length > 1 ? roles[1] : ""))));
                }
                case RENAME -> ph.addAll(List.of("old", new Text.Mini(clanName(part(parts, 0))),
                        "new", new Text.Mini(clanName(part(parts, 1)))));
                case ICON -> ph.addAll(List.of("old", new Text.Mini(icon(part(parts, 0))),
                        "new", new Text.Mini(icon(part(parts, 1)))));
                case PVP -> ph.addAll(List.of("state", mini(text(row.amount() == 1 ? "pvp-on" : "pvp-off", ""))));
                case LEADER -> ph.addAll(List.of("staff_line", row.amount() == 1
                        ? mini(text("leader-by-staff", "")) : new Text.Mini("")));
                case BOOST_START, BOOST_EXTEND, BOOST_REPLACE, BOOST_STOP, BOOST_END -> {
                    long seconds = 0;
                    int old = 0;
                    try {
                        seconds = Long.parseLong(part(parts, 1));
                        old = Integer.parseInt(part(parts, 2));
                    } catch (NumberFormatException ignored) {
                        // older or hand-made rows: 0
                    }
                    ph.addAll(List.of("boost_type", part(parts, 0),
                            "multiplier", ShopActions.tenths((int) row.amount()),
                            "old_multiplier", ShopActions.tenths(old),
                            "duration", Durations.format(seconds, messages)));
                }
                default -> {
                    // detail (reason, item name) and amount are enough
                }
            }
        }
        Object[] placeholders = ph.toArray();
        String name = Text.fill(Text.legacyToMini(text("types." + key + ".name",
                text("types.UNKNOWN.name", "%type%"))), placeholders);
        List<String> lore = new ArrayList<>();
        List<String> lines = menu == null ? List.of() : menu.getStringList("types." + key + ".lore");
        if (lines.isEmpty() && menu != null) lines = menu.getStringList("types.UNKNOWN.lore");
        for (String line : lines) lore.add(Text.fill(Text.legacyToMini(line), placeholders));
        if (menu != null) {
            for (String line : menu.getStringList("entry-item.lore-footer")) lore.add(Text.fill(Text.legacyToMini(line), placeholders));
        }
        return new Sheet(name, lore);
    }
}
