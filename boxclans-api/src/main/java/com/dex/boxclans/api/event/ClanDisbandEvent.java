package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** A clan was disbanded; members have already received a {@link ClanMemberLeaveEvent} with reason {@code DISBAND}. Fired on the main thread. */
public class ClanDisbandEvent extends ClanEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actor;

    public ClanDisbandEvent(Clan clan, UUID actor) {
        super(clan);
        this.actor = actor;
    }

    /** Who disbanded it, {@code null} for the console or an admin command. */
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
