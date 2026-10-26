package com.dex.boxclans.api;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Clan points (§5), registered in Bukkit's {@code ServicesManager}. A <i>source</i> is an id declared in
 * {@code points.yml} ({@code kill}, {@code koth-top1}, {@code quest}…); its rules (points, cooldown, caps,
 * {@code every}, boost) apply to every call.
 *
 * <p><b>Threads:</b> callable from any thread; a call from another thread is moved to the main thread and
 * returns {@link PointResult#QUEUED}. Never throws: no clan gives {@link PointResult#NO_CLAN}.
 */
public interface ClanPoints {

    /** Awards the source's points to the player's clan, counted as the player's contribution. */
    PointResult award(UUID player, String source);

    /** Same as {@link #award(UUID, String)} with {@code amount} instead of the source's points. */
    PointResult award(UUID player, String source, long amount);

    /** Awards the source's points to a whole clan (no contribution), e.g. {@code koth-top1}. */
    PointResult awardClan(UUID clanUuid, String source);

    /** Same as {@link #awardClan(UUID, String)} with {@code amount} instead of the source's points. */
    PointResult awardClan(UUID clanUuid, String source, long amount);

    /**
     * Starts a kill event (§5.4) with a profile from {@code points.yml} ({@code kill-events}). Only one event runs
     * at a time and it is not kept across restarts.
     *
     * @return false when the profile does not exist or an event is already running
     */
    boolean startKillEvent(String profile, Duration duration);

    /** Stops the running kill event, if any. */
    void stopKillEvent();

    /** Profile id of the running kill event. */
    Optional<String> activeKillEvent();
}
