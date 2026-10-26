package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.history.HistoryFormat;
import com.dex.boxclans.history.HistoryType;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * {@code menus/clan-history.yml}: the clan history (BOXCLANS_SHOP_SPEC.md §6.2), one sheet of paper per entry,
 * newest first, filtered by group. Every member reads it (1.4.2; staff: {@code /clanadmin history}).
 * Each page is read from the database off the main thread, so a long history never lags the server.
 */
public final class ClanHistoryMenu extends LayoutMenu {

    private final UUID clanUuid;
    private final boolean staffView;
    /** {@code null} = every group. */
    private HistoryType.Group group;
    private int page;
    private Database.HistoryPage result;
    /** Increases with every request; an older answer is dropped. */
    private int request;

    public ClanHistoryMenu(BoxClansPlugin plugin, Player viewer, UUID clanUuid, boolean staffView) {
        super(plugin, viewer, "clan-history");
        this.clanUuid = clanUuid;
        this.staffView = staffView;
    }

    private ClanData clan() {
        return plugin.manager().clan(clanUuid);
    }

    /** A member of the clan, or staff. */
    private boolean allowed() {
        ClanData clan = clan();
        return clan != null && (staffView || clan.isMember(viewer.getUniqueId()));
    }

    @Override
    public void open() {
        if (!allowed()) {
            plugin.guis().openMain(viewer);
            return;
        }
        super.open();
        load();
    }

    @Override
    protected Object[] titlePlaceholders() {
        ClanData clan = clan();
        return new Object[]{"clan", clan == null ? "" : clan.name().placeholder()};
    }

    private int perPage() {
        return Math.max(1, slots('X').size());
    }

    /** Asks the database for the current page; the menu redraws when it answers. */
    private void load() {
        int id = ++request;
        result = null;
        List<String> types = new ArrayList<>();
        if (group != null) for (HistoryType t : HistoryType.of(group)) types.add(t.key());
        plugin.database().historyPage(clanUuid, types, page * perPage(), perPage()).whenComplete((r, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) plugin.getLogger().log(Level.WARNING, "Could not read the clan history", error);
                    if (id != request) return;
                    result = r == null ? new Database.HistoryPage(List.of(), 0) : r;
                    refresh();
                }));
    }

    private static Text.Mini mini(String legacy) {
        return new Text.Mini(Text.legacyToMini(legacy));
    }

    private int pages() {
        return result == null ? 1 : Math.max(1, (result.total() + perPage() - 1) / perPage());
    }

    @Override
    protected void draw() {
        if (clan() == null) return;
        fillEmpty();
        drawTabs();
        List<Integer> slots = slots('X');
        if (result == null) {
            ItemStack loading = MenuItems.build(section("loading"));
            if (loading != null && !slots.isEmpty()) set(slots.get(0), loading, null);
        } else {
            HistoryFormat format = new HistoryFormat(cfg, plugin.messages(), plugin.roles(), plugin.icons(),
                    plugin.pointsConfig().zone());
            List<Database.HistoryRow> rows = result.rows();
            for (int i = 0; i < rows.size() && i < slots.size(); i++) set(slots.get(i), sheet(format.render(rows.get(i))), null);
            if (rows.isEmpty()) {
                ItemStack empty = MenuItems.build(section("empty"));
                if (empty != null && !slots.isEmpty()) set(slots.get(0), empty, null);
            }
        }
        placeStatic('P', click -> {
            if (page > 0) {
                page--;
                load();
            }
        });
        placeStatic('N', click -> {
            if (page + 1 < pages()) {
                page++;
                load();
            }
        });
        placeStatic('I', null, "page", page + 1, "pages", pages(), "count", result == null ? 0 : result.total());
        placeStatic('B', click -> {
            if (staffView) viewer.closeInventory();
            else plugin.guis().openMain(viewer);
        });
    }

    private void drawTabs() {
        List<HistoryType.Group> tabs = new ArrayList<>();
        tabs.add(null);
        tabs.addAll(List.of(HistoryType.Group.values()));
        List<Integer> slots = slots('T');
        for (int i = 0; i < slots.size() && i < tabs.size(); i++) {
            HistoryType.Group g = tabs.get(i);
            String path = g == null ? "tab-all" : "tabs." + g.name();
            Material m = MenuItems.material(YamlFiles.string(cfg, path + ".material", "PAPER"), Material.PAPER);
            ItemStack item = MenuItems.build(m, section("tab-item"), null,
                    "tab", mini(YamlFiles.string(cfg, path + ".name", g == null ? "Tất cả" : g.name())),
                    "state", mini(text(g == group ? "tab-selected" : "tab-other")));
            if (g == group && item != null) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setEnchantmentGlintOverride(true);
                    item.setItemMeta(meta);
                }
            }
            set(slots.get(i), item, click -> {
                group = g;
                page = 0;
                load();
                refresh();
            });
        }
        drawTabMarkers(tabs.size(), tabs.indexOf(group), i -> {
            group = tabs.get(i);
            page = 0;
            load();
            refresh();
        });
    }

    private ItemStack sheet(HistoryFormat.Sheet s) {
        ConfigurationSection item = section("entry-item");
        Material m = MenuItems.material(item == null ? null : item.getString("material"), Material.PAPER);
        ItemStack paper = MenuItems.stack(m, 1);
        ItemMeta meta = paper.getItemMeta();
        if (meta == null) return paper;
        meta.displayName(Text.noItalic(Text.parse(s.name())));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : s.lore()) lore.add(Text.noItalic(Text.parse(line)));
        meta.lore(lore);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        paper.setItemMeta(meta);
        return paper;
    }

    /** For tests: the page read from the database, {@code null} while loading. */
    public Database.HistoryPage result() {
        return result;
    }
}
