package com.dex.boxclans.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.time.Duration;

/** A kill event (§5.4) started. Fired on the main thread. */
public class ClanKillEventStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String profile;
    private final Duration duration;

    public ClanKillEventStartEvent(String profile, Duration duration) {
        this.profile = profile;
        this.duration = duration;
    }

    /** Profile id from points.yml. */
    public String profile() {
        return profile;
    }

    /** How long it runs. */
    public Duration duration() {
        return duration;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
