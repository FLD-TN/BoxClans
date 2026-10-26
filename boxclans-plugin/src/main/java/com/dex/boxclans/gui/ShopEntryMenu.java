package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.config.ShopConfig;
import com.dex.boxclans.item.Delivery;
import com.dex.boxclans.shop.ShopEditor;
import com.dex.boxclans.shop.ShopEntry;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code menus/shop-entry.yml}: staff edit one shop entry (BOXCLANS_SHOP_SPEC.md §4.6): price, weekly limit,
 * category, delivery, commands, hidden, order, item, delete. Needs {@code boxclans.admin.shop}.
 */
public final class ShopEntryMenu extends LayoutMenu {

    private final int id;
    private final Runnable back;

    public ShopEntryMenu(BoxClansPlugin plugin, Player viewer, int id, Runnable back) {
        super(plugin, viewer, "shop-entry");
        this.id = id;
        this.back = back;
    }

    private ShopEditor editor() {
        return plugin.shopEditor();
    }

    @Override
    public void open() {
        if (!viewer.hasPermission(ClanShopMenu.EDIT_PERMISSION) || plugin.shop().entry(id) == null) {
            back.run();
            return;
        }
        super.open();
    }

    @Override
    protected Object[] titlePlaceholders() {
        return new Object[]{"id", id};
    }

    /** Reopens this editor (after a chat prompt). */
    private Runnable self() {
        return () -> new ShopEntryMenu(plugin, viewer, id, back).open();
    }

    private static Text.Mini mini(String legacy) {
        return new Text.Mini(Text.legacyToMini(legacy));
    }

    @Override
    protected void draw() {
        ShopEntry e = plugin.shop().entry(id);
        if (e == null) return;
        fillEmpty();
        ItemStack item = plugin.shopActions().item(e.item());
        if (item == null) item = MenuItems.stack(Material.BARRIER, 1);
        place('I', item, null);

        ShopConfig.Category category = plugin.shopConfig().category(plugin.shop().categoryOf(e));
        List<Text.Mini> commands = new ArrayList<>();
        for (int i = 0; i < e.commands().size(); i++) {
            commands.add(new Text.Mini(Text.fill(Text.legacyToMini(text("command-line")), "index", i + 1,
                    "command", e.commands().get(i))));
        }
        if (commands.isEmpty()) commands.add(mini(text("no-commands")));
        Object[] ph = {"id", id, "price", Numbers.grouped(e.price()),
                "limit", e.weeklyLimit() <= 0 ? mini(text("no-limit")) : String.valueOf(e.weeklyLimit()),
                "category", mini(category == null ? e.category() : category.name()),
                "mode", mini(text("mode-" + (e.delivery() == Delivery.ITEM ? "item" : "command"))),
                "hidden", mini(text(e.hidden() ? "hidden-yes" : "hidden-no")),
                "commands", commands};

        placeStatic('P', click -> editor().askPrice(viewer, id, self()), ph);
        placeStatic('L', click -> editor().askLimit(viewer, id, self()), ph);
        placeStatic('C', click -> {
            editor().cycleCategory(id, click == ClickType.RIGHT ? -1 : 1);
            refresh();
        }, ph);
        placeStatic('M', click -> {
            editor().toggleDelivery(id);
            refresh();
        }, ph);
        placeStatic('O', click -> {
            if (click == ClickType.RIGHT) {
                editor().removeCommand(id, 0);
                refresh();
            } else {
                editor().askCommand(viewer, id, self());
            }
        }, ph);
        placeStatic('H', click -> {
            editor().toggleHidden(id);
            refresh();
        }, ph);
        placeStatic('U', click -> {
            if (!editor().replaceItem(viewer, id)) plugin.messages().send(viewer, "shop-edit-no-item");
            refresh();
        }, ph);
        placeStatic('<', click -> {
            plugin.shop().move(id, -1);
            refresh();
        }, ph);
        placeStatic('>', click -> {
            plugin.shop().move(id, 1);
            refresh();
        }, ph);
        placeStatic('D', click -> new ConfirmMenu.Request(plugin, viewer, "shop-remove").icon(
                plugin.shopActions().item(e.item()) == null ? MenuItems.stack(Material.BARRIER, 1)
                        : plugin.shopActions().item(e.item())).placeholders("id", id)
                .onCancel(self()).open(() -> {
                    plugin.shop().remove(id);
                    plugin.messages().send(viewer, "shop-entry-removed", "id", id);
                    back.run();
                }), ph);
        placeStatic('B', click -> back.run());
    }
}
