package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** A player left a clan. Fired on the main thread. */
public class ClanMemberLeaveEvent extends ClanEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final Reason reason;

    public ClanMemberLeaveEvent(Clan clan, UUID player, Reason reason) {
        super(clan);
        this.player = player;
        this.reason = reason;
    }

    public enum Reason {
        /** Left on their own. */
        LEAVE,
        /** Kicked by the leader, the vice or an admin. */
        KICK,
        /** Kicked at random after a leader change because the new leader has a lower member limit (§6). */
        OVER_LIMIT,
        /** The clan was disbanded. */
        DISBAND
    }

    /** The former member. */
    public UUID player() {
        return player;
    }

    /** Why. */
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
