package com.dex.boxclans.api;

/**
 * Outcome of a {@link ClanCurrency} call.
 *
 * @param status  what happened
 * @param amount  Minh Huy actually added (positive) or removed (negative), 0 when nothing changed
 * @param balance the clan's balance after the call (0 when there is no clan)
 * @since 1.2.0
 */
public record CurrencyResult(Status status, long amount, long balance) {

    public enum Status {
        /** The balance changed. */
        OK,
        /** The player has no clan, or the clan does not exist. */
        NO_CLAN,
        /** The amount is not a positive number. */
        INVALID_AMOUNT,
        /** {@code take}: the balance is lower than the amount, nothing was removed. */
        NOT_ENOUGH,
        /** The balance would go above {@link ClanCurrency#MAX_BALANCE}, nothing was added. */
        LIMIT,
        /** The call came from another thread and was queued to the main thread; the outcome is not known yet. */
        QUEUED
    }

    public static final CurrencyResult NO_CLAN = new CurrencyResult(Status.NO_CLAN, 0, 0);
    public static final CurrencyResult QUEUED = new CurrencyResult(Status.QUEUED, 0, 0);

    /** True when the balance changed. */
    public boolean success() {
        return status == Status.OK;
    }
}
