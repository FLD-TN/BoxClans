package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A chest GUI. Items are display-only: {@link MenuListener} cancels every click and drag in a menu, so nothing
 * can ever be taken out (§8.3: no dupe path). Main thread only.
 */
public abstract class Menu implements InventoryHolder {

    protected final BoxClansPlugin plugin;
    protected final Player viewer;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    private Inventory inventory;

    protected Menu(BoxClansPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract Component title();

    protected abstract int rows();

    /** Fills the inventory with {@link #set}. */
    protected abstract void draw();

    /** Builds and shows the menu (a new inventory, so the title is fresh). */
    public void open() {
        int rows = Math.max(1, Math.min(6, rows()));
        inventory = Bukkit.createInventory(this, rows * 9, title());
        actions.clear();
        draw();
        viewer.openInventory(inventory);
        plugin.sounds().play(viewer, "ui-open");
    }

    /** Redraws in place (same title). */
    public void refresh() {
        if (inventory == null) return;
        actions.clear();
        inventory.clear();
        draw();
    }

    protected void set(int slot, ItemStack item, Consumer<ClickType> action) {
        if (inventory == null || slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    protected int size() {
        return inventory == null ? 0 : inventory.getSize();
    }

    /** Called by {@link MenuListener} for a click in the top inventory. */
    void click(int slot, ClickType type) {
        Consumer<ClickType> action = actions.get(slot);
        if (action == null) return;
        plugin.sounds().play(viewer, "ui-click");
        action.accept(type);
    }

    /** Called when the viewer closes the inventory (not when another menu replaces it). */
    protected void closed() {
    }

    public Player viewer() {
        return viewer;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
