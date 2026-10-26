package com.dex.boxclans.gui;

import org.bukkit.event.inventory.ClickType;

import java.util.List;

/** Test access to package-private menu internals. */
public final class MenuAccess {

    private MenuAccess() {
    }

    public static void click(Menu menu, int slot, ClickType type) {
        menu.click(slot, type);
    }

    /** Clicks the first slot of a layout character. */
    public static void click(Menu menu, char c, ClickType type) {
        List<Integer> slots = ((LayoutMenu) menu).slots(c);
        if (slots.isEmpty()) throw new IllegalStateException("no slot '" + c + "' in " + menu.getClass().getSimpleName());
        menu.click(slots.get(0), type);
    }

    public static List<Integer> slots(Menu menu, char c) {
        return ((LayoutMenu) menu).slots(c);
    }

    public static org.bukkit.inventory.ItemStack item(Menu menu, int slot) {
        return menu.getInventory().getItem(slot);
    }

    public static Flag draft(FlagMakerMenu menu) {
        return menu.draft();
    }

    public static void closed(Menu menu) {
        menu.closed();
    }

    public static List<String> wrap(String text, int width) {
        return PagedMenu.wrap(text, width);
    }

    public static List<net.kyori.adventure.text.Component> withoutUseLines(List<net.kyori.adventure.text.Component> lore,
                                                                           List<String> hide) {
        return ClanVaultMenu.withoutUseLines(lore, hide);
    }
}
