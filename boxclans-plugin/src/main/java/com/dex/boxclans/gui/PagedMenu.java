package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Names;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A {@link LayoutMenu} with a paged list on the {@code X} slots and the usual sort / view lore lines. */
public abstract class PagedMenu extends LayoutMenu {

    protected int page;

    protected PagedMenu(BoxClansPlugin plugin, Player viewer, String name) {
        super(plugin, viewer, name);
    }

    protected List<Integer> content() {
        return slots('X');
    }

    protected int pages(int total) {
        int per = Math.max(1, content().size());
        return Math.max(1, (total + per - 1) / per);
    }

    /** The items of the current page (the page is clamped first). */
    protected <T> List<T> pageOf(List<T> all) {
        int per = Math.max(1, content().size());
        page = Math.max(0, Math.min(page, pages(all.size()) - 1));
        int from = page * per;
        return new ArrayList<>(all.subList(Math.min(from, all.size()), Math.min(from + per, all.size())));
    }

    /** Previous / next buttons ({@code items.<prev>}, {@code items.<next>}). */
    protected void paging(char prev, char next, int total) {
        placeStatic(prev, click -> {
            if (page > 0) {
                page--;
                refresh();
            }
        });
        placeStatic(next, click -> {
            if (page + 1 < pages(total)) {
                page++;
                refresh();
            }
        });
    }

    /**
     * Lore placeholders {@code %<prefix>_<option>%} for a cycling choice: the active one with
     * {@code <prefix>-active-prefix}, the others with {@code <prefix>-inactive-prefix}, labels from
     * {@code <prefix>-labels.<option>}.
     */
    protected Object[] choiceLines(String prefix, List<String> options, String active) {
        List<Object> out = new ArrayList<>();
        for (String o : options) {
            boolean on = o.equals(active);
            String line = text(prefix + (on ? "-active-prefix" : "-inactive-prefix")) + text(prefix + "-labels." + o);
            out.add(prefix + "_" + o.replace('-', '_'));
            out.add(new Text.Mini(Text.legacyToMini(line)));
        }
        return out.toArray();
    }

    /** Joins placeholder arrays. */
    protected static Object[] join(Object[]... parts) {
        List<Object> out = new ArrayList<>();
        for (Object[] p : parts) out.addAll(List.of(p));
        return out.toArray();
    }

    protected static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The clan icon decorated with a section: its flag, or {@code flag.default-material}. */
    protected ItemStack clanIcon(ClanSnapshot clan, String sectionPath, Object... placeholders) {
        ItemStack icon = Flag.icon(clan.flag(), MenuItems.material(plugin.settings().flagDefaultMaterial(),
                Material.WHITE_BANNER));
        return MenuItems.decorate(icon, section(sectionPath), null, placeholders);
    }

    /** Placeholders shared by every clan item. */
    protected Object[] clanPlaceholders(ClanSnapshot clan) {
        String unlimited = text("unlimited").isEmpty() ? "∞" : text("unlimited");
        String desc = clan.description().isEmpty() ? text("empty-description") : clan.description();
        return new Object[]{
                "clan", clan.clanName().placeholder(),
                "clan_name", clan.clanName().placeholder(),
                "key", clan.key(),
                "description", desc,
                "members", clan.size(),
                "max_members", clan.maxMembers() < 0 ? unlimited : String.valueOf(clan.maxMembers()),
                "score", clan.points(),
                "rank", plugin.index().rank(clan.uuid()),
                "leader", Names.of(clan.leader()),
                "owner", Names.of(clan.leader()),
                "minhhuy", com.dex.boxclans.util.Numbers.grouped(clan.minhHuy()),
                "description_lines", descriptionLines(desc)};
    }

    /** {@code %description_lines%} (1.4.3): the description cut into lines of {@code description-wrap} characters. */
    protected List<Text.Mini> descriptionLines(String desc) {
        int width = (int) Math.max(10, com.dex.boxclans.config.YamlFiles.integer(cfg, "description-wrap", 30));
        String color = Text.legacyToMini(com.dex.boxclans.config.YamlFiles.string(cfg, "description-color", "&#FFFFFF"));
        List<Text.Mini> out = new ArrayList<>();
        for (String line : wrap(desc, width)) out.add(new Text.Mini(color + Text.MM.escapeTags(line)));
        return out;
    }

    /** Word wrap: lines of at most {@code width} characters, a longer word is cut. */
    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            while (word.length() > width) {
                if (!line.isEmpty()) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                out.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (line.isEmpty()) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= width) {
                line.append(' ').append(word);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }
}
