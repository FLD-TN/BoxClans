package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** The clan's friendly fire switch changed (§4C). Fired on the main thread. */
public class ClanFriendlyFireChangeEvent extends ClanEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean enabled;
    private final UUID actor;

    public ClanFriendlyFireChangeEvent(Clan clan, boolean enabled, UUID actor) {
        super(clan);
        this.enabled = enabled;
        this.actor = actor;
    }

    /** New state. */
    public boolean enabled() {
        return enabled;
    }

    /** Who changed it, {@code null} for the console. */
    public UUID actor() {
        return actor;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
