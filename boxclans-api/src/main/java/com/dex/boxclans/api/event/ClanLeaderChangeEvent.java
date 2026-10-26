package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** The Minh Chủ changed. Fired after the {@link ClanRoleChangeEvent}s of both players. Fired on the main thread. */
public class ClanLeaderChangeEvent extends ClanEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID oldLeader;
    private final UUID newLeader;

    public ClanLeaderChangeEvent(Clan clan, UUID oldLeader, UUID newLeader) {
        super(clan);
        this.oldLeader = oldLeader;
        this.newLeader = newLeader;
    }

    /** Previous Minh Chủ (now Thành Viên). */
    public UUID oldLeader() {
        return oldLeader;
    }

    /** New Minh Chủ. */
    public UUID newLeader() {
        return newLeader;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
