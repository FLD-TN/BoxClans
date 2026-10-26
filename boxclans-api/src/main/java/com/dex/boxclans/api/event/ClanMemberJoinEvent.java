package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** A player joined a clan (accepted an invite). Fired on the main thread. */
public class ClanMemberJoinEvent extends ClanEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;

    public ClanMemberJoinEvent(Clan clan, UUID player) {
        super(clan);
        this.player = player;
    }

    /** The new member. */
    public UUID player() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
