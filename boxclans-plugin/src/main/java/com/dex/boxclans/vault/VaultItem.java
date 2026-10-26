package com.dex.boxclans.vault;

import com.dex.boxclans.item.Delivery;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A stack in a clan storage (BOXCLANS_SHOP_SPEC.md §5): its own copy of the item and of the commands, so editing or
 * removing the shop entry it came from never changes it. Immutable: the amount changes make a new value.
 *
 * @param id       stable id (shown to staff for {@code /clanadmin kho remove})
 * @param category {@code shop.yml} category id
 * @param item     encoded item, amount 1
 * @param amount   how many, at least 1
 */
public record VaultItem(long id, UUID clan, String category, Delivery delivery, byte[] item, List<String> commands,
                        int amount, long createdAt) {

    public VaultItem {
        commands = List.copyOf(commands);
    }

    /** Same item, delivery, commands and category: the two merge into one stack (§5.2). */
    public boolean sameAs(String category, Delivery delivery, byte[] item, List<String> commands) {
        return this.category.equals(category) && this.delivery == delivery && Arrays.equals(this.item, item)
                && this.commands.equals(commands);
    }

    public VaultItem withAmount(int n) {
        return new VaultItem(id, clan, category, delivery, item, commands, n, createdAt);
    }
}
