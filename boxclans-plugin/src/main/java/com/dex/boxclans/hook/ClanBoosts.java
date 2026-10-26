package com.dex.boxclans.hook;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Clan boosts of BoxCore 1.5.0 as BoxClans sees them (BOXCLANS_SHOP_SPEC.md §5.3, §5.6). Only
 * {@link BoxCoreClansHook} talks to BoxCore; everything else (storage, commands, tests) uses this interface, so
 * BoxCore stays optional. Main thread only.
 */
public interface ClanBoosts {

    /** A BoxCore bottle: type id, multiplier in tenths, duration, and whether it is a clan bottle. */
    record Bottle(String type, int tenths, long seconds, boolean clan) {}

    /** Mirror of BoxCore's {@code ClanBoostResult.Status}. */
    enum Status {
        STARTED, EXTENDED, REPLACED, CONFIRM_REPLACE, CONFIRM_CAPPED, LOWER, FULL, INVALID, UNAVAILABLE;

        public boolean applied() {
            return this == STARTED || this == EXTENDED || this == REPLACED;
        }
    }

    /**
     * @param oldTenths    the running boost before the call, 0 if none
     * @param oldExpiresAt its expiry, epoch ms
     * @param lostSeconds  time dropped (cut at max-duration, or the replaced boost's remaining time)
     */
    record Result(Status status, int oldTenths, long oldExpiresAt, long lostSeconds) {}

    /** The bottle data of an item, {@code null} when it is not a BoxCore bottle. */
    Bottle readBottle(ItemStack item);

    /** Starts, extends or replaces the clan's boost (BoxCore tells the clan). */
    Result start(UUID clan, String type, int tenths, long seconds, boolean confirmed, String actor);

    /** Stops the clan's boost of a type. @return false if none was running */
    boolean stop(UUID clan, String type);

    /** True when BoxCore declares the boost type. */
    boolean isType(String type);
}
