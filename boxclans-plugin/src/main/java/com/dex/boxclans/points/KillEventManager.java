package com.dex.boxclans.points;

import com.dex.boxclans.api.ClanPointsImpl;
import com.dex.boxclans.api.event.ClanKillEventEndEvent;
import com.dex.boxclans.api.event.ClanKillEventStartEvent;
import com.dex.boxclans.clan.ClanManager;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The kill event frame (§5.4): one event at a time, server-wide, not kept across restarts. While it runs, kills
 * and deaths use the profile's points instead of the normal kill rules. Start / stop on the main thread.
 */
public final class KillEventManager implements ClanPointsImpl.KillEventControl {

    /** Schedules the automatic end. */
    @FunctionalInterface
    public interface Scheduler {
        Runnable schedule(Runnable task, long delayMillis);
    }

    /** The running event. */
    public record Active(PointsConfig.KillEventProfile profile, long startedAt, long endsAt) {}

    private final Supplier<PointsConfig> config;
    private final ClanManager.Events events;
    private final LongSupplier clock;
    private final Scheduler scheduler;
    private volatile Active active;
    private Runnable cancelTask;

    public KillEventManager(Supplier<PointsConfig> config, ClanManager.Events events, LongSupplier clock,
                            Scheduler scheduler) {
        this.config = config;
        this.events = events;
        this.clock = clock;
        this.scheduler = scheduler;
    }

    @Override
    public boolean start(String profileId, Duration duration) {
        PointsConfig.KillEventProfile profile = config.get().killEvent(profileId);
        if (profile == null || active != null || duration == null || duration.isZero() || duration.isNegative()) {
            return false;
        }
        long now = clock.getAsLong();
        active = new Active(profile, now, now + duration.toMillis());
        cancelTask = scheduler.schedule(this::expire, duration.toMillis());
        events.call(new ClanKillEventStartEvent(profile.id(), duration));
        return true;
    }

    @Override
    public void stop() {
        end(ClanKillEventEndEvent.Reason.STOPPED);
    }

    /** Ends the event when its time is over (scheduled task, or a check). */
    public void expire() {
        Active a = active;
        if (a != null && clock.getAsLong() >= a.endsAt()) end(ClanKillEventEndEvent.Reason.EXPIRED);
    }

    private void end(ClanKillEventEndEvent.Reason reason) {
        Active a = active;
        if (a == null) return;
        active = null;
        if (cancelTask != null) {
            cancelTask.run();
            cancelTask = null;
        }
        events.call(new ClanKillEventEndEvent(a.profile().id(), reason));
    }

    @Override
    public Optional<String> active() {
        Active a = active;
        return a == null ? Optional.empty() : Optional.of(a.profile().id());
    }

    /** The running event, {@code null} if none (any thread). */
    public Active current() {
        Active a = active;
        if (a != null && clock.getAsLong() >= a.endsAt()) return null;
        return a;
    }
}
