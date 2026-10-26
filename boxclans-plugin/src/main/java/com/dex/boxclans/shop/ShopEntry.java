package com.dex.boxclans.shop;

import com.dex.boxclans.item.Delivery;

import java.util.List;

/**
 * An item sold in the clan shop (BOXCLANS_SHOP_SPEC.md §4.2), added in game. Immutable: edits make a new entry.
 *
 * @param id          stable id shown to staff ({@code #12})
 * @param category    {@code shop.yml} category id
 * @param item        the item as added, encoded ({@link com.dex.boxclans.item.ItemCodec}), amount 1
 * @param delivery    what a withdrawal from the storage does
 * @param commands    console commands of a {@link Delivery#COMMAND} entry ({@code {player}}, {@code {clan}}, {@code {amount}})
 * @param price       Minh Huy, at least 1
 * @param weeklyLimit purchases per clan per week, 0 = no limit
 * @param order       position inside the category
 * @param hidden      not shown to players (staff still see it in edit mode)
 */
public record ShopEntry(int id, String category, byte[] item, Delivery delivery, List<String> commands, long price,
                        int weeklyLimit, int order, boolean hidden) {

    public ShopEntry {
        commands = List.copyOf(commands);
    }

    public ShopEntry withCategory(String c) {
        return new ShopEntry(id, c, item, delivery, commands, price, weeklyLimit, order, hidden);
    }

    public ShopEntry withItem(byte[] i) {
        return new ShopEntry(id, category, i, delivery, commands, price, weeklyLimit, order, hidden);
    }

    public ShopEntry withDelivery(Delivery d) {
        return new ShopEntry(id, category, item, d, commands, price, weeklyLimit, order, hidden);
    }

    public ShopEntry withCommands(List<String> c) {
        return new ShopEntry(id, category, item, delivery, c, price, weeklyLimit, order, hidden);
    }

    public ShopEntry withPrice(long p) {
        return new ShopEntry(id, category, item, delivery, commands, p, weeklyLimit, order, hidden);
    }

    public ShopEntry withWeeklyLimit(int l) {
        return new ShopEntry(id, category, item, delivery, commands, price, l, order, hidden);
    }

    public ShopEntry withOrder(int o) {
        return new ShopEntry(id, category, item, delivery, commands, price, weeklyLimit, o, hidden);
    }

    public ShopEntry withHidden(boolean h) {
        return new ShopEntry(id, category, item, delivery, commands, price, weeklyLimit, order, h);
    }
}
