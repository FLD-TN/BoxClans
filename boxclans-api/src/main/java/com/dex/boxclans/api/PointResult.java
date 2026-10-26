package com.dex.boxclans.api;

/**
 * Outcome of a {@link ClanPoints} call. {@link #amount()} is the number of points actually added (or removed,
 * when negative) to the clan; 0 when nothing changed.
 *
 * @param status what happened
 * @param amount points actually applied to the clan
 * @param total  clan points after the call (0 when there is no clan)
 */
public record PointResult(Status status, long amount, long total) {

    public enum Status {
        /** Points were applied ({@code amount} may have been cut to fit a cap). */
        SUCCESS,
        /** The player has no clan, or the clan does not exist. */
        NO_CLAN,
        /** The source is not declared in {@code points.yml}. */
        UNKNOWN_SOURCE,
        /** The source is declared with {@code enabled: false}. */
        SOURCE_DISABLED,
        /** The source's {@code cooldown} for this player has not elapsed. */
        COOLDOWN,
        /** The player reached the source's {@code daily-cap}. */
        DAILY_CAP,
        /** The clan reached the source's {@code clan-daily-cap}. */
        CLAN_DAILY_CAP,
        /** Counted towards {@code every}; points are added once the count is reached. */
        ACCUMULATED,
        /** A {@code ClanPointsGainEvent} listener cancelled the gain. */
        CANCELLED,
        /** Blocked by the kill rules (§7). */
        BLOCKED,
        /** Nothing to apply (0 points, or a loss clamped by {@code allow-negative: false}). */
        ZERO,
        /** The call came from another thread and was queued to the main thread; the outcome is not known yet. */
        QUEUED
    }

    public static final PointResult NO_CLAN = new PointResult(Status.NO_CLAN, 0, 0);
    public static final PointResult UNKNOWN_SOURCE = new PointResult(Status.UNKNOWN_SOURCE, 0, 0);
    public static final PointResult QUEUED = new PointResult(Status.QUEUED, 0, 0);

    /** True when points were applied. */
    public boolean success() {
        return status == Status.SUCCESS;
    }
}
