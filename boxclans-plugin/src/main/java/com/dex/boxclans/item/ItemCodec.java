package com.dex.boxclans.item;

import org.bukkit.inventory.ItemStack;

/**
 * Stores an item as bytes and back (BOXCLANS_SHOP_SPEC.md §5.5): one item (amount 1) with everything it carries
 * (name, lore, enchantments, NBT / PDC of other plugins). Two equal items give equal bytes, which is how the storage
 * merges them. Replaced by tests (Paper's serialization needs the server).
 */
public interface ItemCodec {

    byte[] encode(ItemStack item);

    /** A fresh item of amount 1. */
    ItemStack decode(byte[] data);

    /** Paper: {@link ItemStack#serializeAsBytes()} (data-fixed when the server updates). */
    ItemCodec PAPER = new ItemCodec() {
        @Override
        public byte[] encode(ItemStack item) {
            return item.asOne().serializeAsBytes();
        }

        @Override
        public ItemStack decode(byte[] data) {
            return ItemStack.deserializeBytes(data);
        }
    };
}
