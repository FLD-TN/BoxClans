package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code menus/confirm.yml}: confirm / cancel an action (create, disband, kick, leave, transfer-leader,
 * swap-vice). {@code I} shows {@code info.<action>} with its placeholders; closing the menu cancels.
 */
public final class ConfirmMenu extends LayoutMenu {

    private final String action;
    private final Object[] placeholders;
    private final List<String> extraLore;
    private final ItemStack icon;
    private final OfflinePlayer head;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private boolean decided;

    private ConfirmMenu(BoxClansPlugin plugin, Player viewer, String action, ItemStack icon, OfflinePlayer head,
                        List<String> extraLore, Runnable onConfirm, Runnable onCancel, Object[] placeholders) {
        super(plugin, viewer, "confirm");
        this.action = action;
        this.icon = icon;
        this.head = head;
        this.extraLore = extraLore;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.placeholders = placeholders;
    }

    /** Builder-ish entry point. */
    public static final class Request {
        private final BoxClansPlugin plugin;
        private final Player viewer;
        private final String action;
        private ItemStack icon;
        private OfflinePlayer head;
        private final List<String> extraLore = new ArrayList<>();
        private Runnable onCancel;
        private Object[] placeholders = new Object[0];

        public Request(BoxClansPlugin plugin, Player viewer, String action) {
            this.plugin = plugin;
            this.viewer = viewer;
            this.action = action;
        }

        /** Item used for {@code I} instead of {@code info.<action>.material} (e.g. the clan flag). */
        public Request icon(ItemStack icon) {
            this.icon = icon;
            return this;
        }

        public Request head(OfflinePlayer head) {
            this.head = head;
            return this;
        }

        /** Appends {@code info.<action>.<path>} lines to the info lore (e.g. the over-limit warning). */
        public Request extra(String path) {
            ConfigurationSection info = plugin.menus().menu("confirm").getConfigurationSection("info." + action);
            if (info != null) extraLore.addAll(info.getStringList(path));
            return this;
        }

        public Request placeholders(Object... placeholders) {
            this.placeholders = placeholders;
            return this;
        }

        public Request onCancel(Runnable onCancel) {
            this.onCancel = onCancel;
            return this;
        }

        public void open(Runnable onConfirm) {
            new ConfirmMenu(plugin, viewer, action, icon, head, extraLore, onConfirm, onCancel, placeholders).open();
        }
    }

    @Override
    protected Object[] titlePlaceholders() {
        Object[] out = new Object[placeholders.length + 2];
        System.arraycopy(placeholders, 0, out, 0, placeholders.length);
        out[placeholders.length] = "action";
        out[placeholders.length + 1] = new Text.Mini(Text.legacyToMini(text("action-labels." + action)));
        return out;
    }

    @Override
    protected void draw() {
        fillEmpty();
        placeStatic('C', click -> decide(true), placeholders);
        placeStatic('D', click -> decide(false), placeholders);
        ConfigurationSection info = section("info." + action);
        if (info != null) {
            ItemStack item;
            if (icon != null) {
                item = MenuItems.decorate(icon.clone(), info, head, placeholders);
            } else {
                Material m = MenuItems.material(info.getString("material"), Material.PAPER);
                item = MenuItems.build(m, info, head, placeholders);
            }
            if (!extraLore.isEmpty()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                    lore.addAll(MenuItems.lore(extraLore, placeholders));
                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
            }
            place('I', item, null);
        }
    }

    private void decide(boolean confirm) {
        if (decided) return;
        decided = true;
        viewer.closeInventory();
        if (confirm) {
            onConfirm.run();
        } else if (onCancel != null) {
            onCancel.run();
        }
    }

    @Override
    protected void closed() {
        if (decided) return;
        decided = true;
        if (onCancel != null) onCancel.run();
    }
}
