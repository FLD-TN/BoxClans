package com.dex.boxclans.item;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/** Room checks for items given to a player (storage withdrawals): nothing is ever dropped on the ground. */
public final class Inventories {

    private Inventories() {
    }

    /** True when one {@code item} fits: an empty storage slot, or a similar stack below its maximum. */
    public static boolean hasRoom(Player player, ItemStack item) {
        PlayerInventory inv = player.getInventory();
        if (inv == null || item == null) return false;
        ItemStack[] slots = inv.getStorageContents();
        if (slots == null) return false;
        for (ItemStack slot : slots) {
            if (slot == null || slot.getType() == Material.AIR || slot.getAmount() <= 0) return true;
            if (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) return true;
        }
        return false;
    }

    /**
     * Gives the item; the caller checked {@link #hasRoom}. Should something still not fit (another plugin changed
     * the inventory in between), it is dropped at the player's feet rather than lost.
     */
    public static void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(item);
        if (left == null) return;
        for (ItemStack rest : left.values()) player.getWorld().dropItemNaturally(player.getLocation(), rest);
    }
}
