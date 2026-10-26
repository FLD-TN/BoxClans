package com.dex.boxclans.api;

import java.util.UUID;

/**
 * Minh Huy, the currency shared by a whole clan (BOXCLANS_SHOP_SPEC.md §2), registered in Bukkit's
 * {@code ServicesManager}. No player owns Minh Huy: it is earned by the clan through staff commands and plugins (KOTH,
 * events) and spent by the Minh Chủ / Phó Bang in the clan shop. Balances are whole numbers from 0 to
 * {@value #MAX_BALANCE}.
 *
 * <p><b>Threads:</b> callable from any thread; a change requested from another thread is moved to the main thread
 * and returns {@link CurrencyResult#QUEUED}. Never throws. The clan's online members are told about every change made
 * through this API (the staff command has {@code -s} to stay silent); every change is kept in the clan history.
 *
 * @since 1.2.0
 */
public interface ClanCurrency {

    /** Largest balance of a clan. */
    long MAX_BALANCE = 1_000_000_000_000L;

    /** The clan's balance, 0 when the clan does not exist. Any thread. */
    long balance(UUID clanUuid);

    /** Adds {@code amount} (&gt; 0) to the clan of {@code player}. {@code NO_CLAN} when the player has none. */
    CurrencyResult give(UUID player, long amount, String reason);

    /** Adds {@code amount} (&gt; 0) to a clan. */
    CurrencyResult giveClan(UUID clanUuid, long amount, String reason);

    /** Removes {@code amount} (&gt; 0) from a clan; {@code NOT_ENOUGH} (nothing removed) when the balance is lower. */
    CurrencyResult take(UUID clanUuid, long amount, String reason);
}
