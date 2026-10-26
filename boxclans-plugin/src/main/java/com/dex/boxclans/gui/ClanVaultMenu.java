package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.config.ShopConfig;
import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.hook.ClanBoosts;
import com.dex.boxclans.item.Delivery;
import com.dex.boxclans.shop.ShopActions;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Numbers;
import com.dex.boxclans.vault.VaultItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code menus/clan-vault.yml}: the clan storage (BOXCLANS_SHOP_SPEC.md §5). Everyone in the clan sees it; the Minh
 * Chủ and the Phó Bang take items out (one per click) or use a clan boost bottle (left click). Other members only look:
 * a click does nothing (1.4.2). Staff open any clan's storage read-only with {@code /clanadmin kho view}.
 * Lines of an item's own lore that tell how to use it ({@code hide-lore-containing}) are hidden here: in the storage a
 * click withdraws, and the item taken out keeps its full lore.
 */
public final class ClanVaultMenu extends PagedMenu {

    private final UUID clanUuid;
    private final boolean staffView;
    private String tab = ShopConfig.ALL;

    public ClanVaultMenu(BoxClansPlugin plugin, Player viewer, UUID clanUuid, boolean staffView) {
        super(plugin, viewer, "clan-vault");
        this.clanUuid = clanUuid;
        this.staffView = staffView;
    }

    private ClanData clan() {
        return plugin.manager().clan(clanUuid);
    }

    @Override
    public void open() {
        ClanData clan = clan();
        if (clan == null || (!staffView && !clan.isMember(viewer.getUniqueId()))) {
            plugin.guis().openMain(viewer);
            return;
        }
        super.open();
    }

    @Override
    protected Object[] titlePlaceholders() {
        ClanData clan = clan();
        return new Object[]{"clan", clan == null ? "" : clan.name().placeholder()};
    }

    private static Text.Mini mini(String legacy) {
        return new Text.Mini(Text.legacyToMini(legacy));
    }

    private Runnable back() {
        String t = tab;
        int pg = page;
        return () -> {
            ClanVaultMenu m = new ClanVaultMenu(plugin, viewer, clanUuid, staffView);
            m.tab = t;
            m.page = pg;
            m.open();
        };
    }

    @Override
    protected void draw() {
        ClanData clan = clan();
        if (clan == null) return;
        fillEmpty();
        drawTabs();
        placeStatic('M', null, "balance", Numbers.grouped(clan.minhHuy()),
                "count", Numbers.grouped(plugin.vault().count(clanUuid)), "clan", clan.name().placeholder());

        boolean manages = !staffView && ShopActions.manages(clan, viewer.getUniqueId());
        List<VaultItem> items = plugin.vault().items(clanUuid, tab.equals(ShopConfig.ALL) ? null : tab,
                plugin.shopConfig()::itemCategory);
        List<Integer> slots = content();
        List<VaultItem> shown = pageOf(items);
        for (int i = 0; i < shown.size() && i < slots.size(); i++) {
            VaultItem v = shown.get(i);
            ClanBoosts.Bottle bottle = plugin.shopActions().clanBottle(v);
            // members and staff only look
            set(slots.get(i), display(v, bottle, manages), !manages ? null : click -> {
                if (bottle != null) {
                    // 1.4.3: a clan bottle is used with a left click; taking it out is off unless bottle-withdraw
                    if (click == ClickType.LEFT) {
                        plugin.shopActions().use(viewer, v.id(), false, back());
                    } else if (bottleWithdraw()) {
                        plugin.shopActions().withdraw(viewer, v.id());
                    } else {
                        return;
                    }
                } else {
                    plugin.shopActions().withdraw(viewer, v.id());
                }
                refresh();
            });
        }
        if (items.isEmpty()) {
            ItemStack empty = MenuItems.build(section("empty"));
            if (empty != null && !slots.isEmpty()) set(slots.get(0), empty, null);
        }
        paging('P', 'N', items.size());
        placeStatic('B', click -> {
            if (staffView) viewer.closeInventory();
            else plugin.guis().openMain(viewer);
        });
    }

    private void drawTabs() {
        List<String> tabs = new ArrayList<>();
        tabs.add(ShopConfig.ALL);
        for (ShopConfig.Category c : plugin.shopConfig().itemCategories()) tabs.add(c.id());
        List<Integer> slots = slots('T');
        for (int i = 0; i < slots.size() && i < tabs.size(); i++) {
            String id = tabs.get(i);
            ShopConfig.Category c = plugin.shopConfig().category(id);
            Material m = c == null ? MenuItems.material(YamlFiles.string(cfg, "tab-all.material", "NETHER_STAR"), Material.NETHER_STAR)
                    : MenuItems.material(c.material(), Material.CHEST);
            String name = c == null ? YamlFiles.string(cfg, "tab-all.name", "Tất cả") : c.name();
            ItemStack item = MenuItems.build(m, section("tab-item"), null, "tab", mini(name),
                    "state", mini(text(id.equals(tab) ? "tab-selected" : "tab-other")));
            if (id.equals(tab) && item != null) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setEnchantmentGlintOverride(true);
                    item.setItemMeta(meta);
                }
            }
            set(slots.get(i), item, click -> {
                tab = id;
                page = 0;
                refresh();
            });
        }
        drawTabMarkers(tabs.size(), tabs.indexOf(tab), i -> {
            tab = tabs.get(i);
            page = 0;
            refresh();
        });
    }

    /** {@code bottle-withdraw}: whether a right click takes a clan bottle out of the storage (off in 1.4.3). */
    private boolean bottleWithdraw() {
        return YamlFiles.bool(cfg, "bottle-withdraw", false);
    }

    private ItemStack display(VaultItem v, ClanBoosts.Bottle bottle, boolean manages) {
        ItemStack item = plugin.shopActions().item(v.item());
        if (item == null) item = MenuItems.stack(Material.BARRIER, 1);
        ConfigurationSection s = section("vault-item");
        if (s == null) return item;
        List<String> lines = new ArrayList<>(s.getStringList("lore"));
        if (staffView) lines.addAll(s.getStringList("lore-staff"));
        else if (!manages) lines.addAll(s.getStringList("lore-member"));
        else if (bottle != null) lines.addAll(s.getStringList("lore-bottle"));
        else if (v.delivery() == Delivery.COMMAND) lines.addAll(s.getStringList("lore-command"));
        else lines.addAll(s.getStringList("lore-item"));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        List<Component> lore = withoutUseLines(meta.lore(), cfg.getStringList("hide-lore-containing"));
        lore.addAll(MenuItems.lore(lines, "amount", Numbers.grouped(v.amount()), "id", v.id()));
        meta.lore(lore);
        item.setItemMeta(meta);
        item.setAmount(Math.max(1, Math.min(v.amount(), item.getMaxStackSize())));
        return item;
    }

    /**
     * The item's own lore without the lines containing a {@code hide-lore-containing} text (any case, colours
     * ignored), and without the blank lines left at the end.
     */
    static List<Component> withoutUseLines(List<Component> lore, List<String> hideContaining) {
        List<Component> out = new ArrayList<>();
        if (lore == null) return out;
        List<String> hide = new ArrayList<>();
        for (String h : hideContaining) if (!h.isBlank()) hide.add(h.toLowerCase(Locale.ROOT));
        for (Component line : lore) {
            String plain = Text.plain(line).toLowerCase(Locale.ROOT);
            if (hide.stream().noneMatch(plain::contains)) out.add(line);
        }
        while (!out.isEmpty() && Text.plain(out.get(out.size() - 1)).isBlank()) out.remove(out.size() - 1);
        return out;
    }
}
