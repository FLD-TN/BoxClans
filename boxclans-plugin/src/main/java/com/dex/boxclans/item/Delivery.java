package com.dex.boxclans.item;

import java.util.Locale;

/** What taking a shop item out of the clan storage does (BOXCLANS_SHOP_SPEC.md §4.2). */
public enum Delivery {
    /** The withdrawing player receives the stored item. */
    ITEM,
    /** The commands run from the console for the withdrawing player; the item is only a picture (virtual keys). */
    COMMAND;

    public static Delivery of(String name) {
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return ITEM;
        }
    }
}
