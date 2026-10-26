package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.config.Icons;
import com.dex.boxclans.config.ShopConfig;
import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.hook.ItemsAdderHook;
import com.dex.boxclans.shop.ShopActions;
import com.dex.boxclans.text.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code menus/clan-icons.yml}: equip a shield icon (BOXCLANS_SHOP_SPEC.md §3), opened from the clan settings. Shows
 * the default icons and the icons the clan owns; the Minh Chủ and the Phó Bang equip or remove, members only look.
 * More icons are bought in the shop.
 */
public final class ClanIconMenu extends PagedMenu {

    public ClanIconMenu(BoxClansPlugin plugin, Player viewer) {
        super(plugin, viewer, "clan-icons");
    }

    private ClanData clan() {
        return plugin.manager().of(viewer.getUniqueId());
    }

    @Override
    public void open() {
        if (clan() == null) {
            plugin.guis().openList(viewer);
            return;
        }
        super.open();
    }

    private static Text.Mini mini(String legacy) {
        return new Text.Mini(Text.legacyToMini(legacy));
    }

    private Text.Mini glyph(String icon) {
        String g = plugin.iconGlyphs().stillRaw(icon);
        return new Text.Mini(g.isEmpty() ? "" : "<white>" + Text.MM.escapeTags(g) + "</white> ");
    }

    /** Default icons first, then owned ones, in icons.yml order. */
    private List<Icons.Icon> available(ClanData clan) {
        List<Icons.Icon> out = new ArrayList<>();
        for (Icons.Icon i : plugin.icons().all()) if (i.defaultOwned()) out.add(i);
        for (Icons.Icon i : plugin.icons().all()) if (!i.defaultOwned() && clan.ownedIcons().contains(i.id())) out.add(i);
        return out;
    }

    @Override
    protected void draw() {
        ClanData clan = clan();
        if (clan == null) return;
        boolean manages = ShopActions.manages(clan, viewer.getUniqueId());
        fillEmpty();
        Icons.Icon current = plugin.icons().icon(clan.icon());
        Object currentText = current == null ? mini(text("no-icon"))
                : new Text.Mini(glyph(current.id()).value() + Text.legacyToMini(current.display()));
        placeStatic('T', null, "current", currentText);

        List<Icons.Icon> all = available(clan);
        List<Integer> slots = content();
        List<Icons.Icon> shown = pageOf(all);
        for (int i = 0; i < shown.size() && i < slots.size(); i++) {
            Icons.Icon icon = shown.get(i);
            set(slots.get(i), iconItem(clan, icon, manages), click -> {
                plugin.actions().chooseIcon(viewer, icon.id());
                refresh();
            });
        }
        paging('P', 'N', all.size());
        ItemStack remove = MenuItems.build(itemSection('R'));
        if (!manages && remove != null) {
            ItemMeta meta = remove.getItemMeta();
            List<String> lore = itemSection('R').getStringList("lore-member");
            if (meta != null && !lore.isEmpty()) {
                meta.lore(MenuItems.lore(lore));
                remove.setItemMeta(meta);
            }
        }
        place('R', remove, click -> {
            plugin.actions().chooseIcon(viewer, "");
            refresh();
        });
        ShopConfig.Category iconTab = plugin.shopConfig().iconCategory();
        placeStatic('S', click -> new ClanShopMenu(plugin, viewer, iconTab == null ? ShopConfig.ALL : iconTab.id()).open());
        placeStatic('B', click -> plugin.guis().openSettings(viewer));
    }

    private ItemStack iconItem(ClanData clan, Icons.Icon icon, boolean manages) {
        ConfigurationSection s = section("icon-item");
        boolean selected = icon.id().equals(clan.icon());
        String state = selected ? text("state-selected") : manages ? text("state-use") : text("state-member");
        Object[] ph = {"display", mini(icon.display()), "icon_static", glyph(icon.id()),
                "clan", clan.name().placeholder(), "state", mini(state)};
        ItemsAdderHook ia = plugin.itemsAdder();
        ItemStack base = ia == null ? null : ia.item(icon.menuItem());
        if (base == null) base = MenuItems.stack(MenuItems.material(icon.fallbackMaterial(), Material.SHIELD), 1);
        ItemStack item = MenuItems.decorate(base, s, null, ph);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && selected && s != null && YamlFiles.bool(s, "glint-when-selected", true)) {
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }
}
