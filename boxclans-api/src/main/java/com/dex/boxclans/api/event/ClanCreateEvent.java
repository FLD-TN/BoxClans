package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** A clan was created. Fired on the main thread. */
public class ClanCreateEvent extends ClanEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID creator;

    public ClanCreateEvent(Clan clan, UUID creator) {
        super(clan);
        this.creator = creator;
    }

    /** The Minh Chủ who created it. */
    public UUID creator() {
        return creator;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
