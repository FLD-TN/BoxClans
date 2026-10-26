package com.dex.boxclans.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** The kill event (§5.4) ended. Fired on the main thread. */
public class ClanKillEventEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String profile;
    private final Reason reason;

    public ClanKillEventEndEvent(String profile, Reason reason) {
        this.profile = profile;
        this.reason = reason;
    }

    public enum Reason {
        /** Its duration elapsed. */
        EXPIRED,
        /** Stopped by a command or the API. */
        STOPPED
    }

    /** Profile id. */
    public String profile() {
        return profile;
    }

    /** Why it ended. */
    public Reason reason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
