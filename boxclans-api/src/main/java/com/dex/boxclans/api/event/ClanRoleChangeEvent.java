package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import com.dex.boxclans.api.ClanRole;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** A member's role changed (promote, demote, swap of Phó Bang, leader transfer). Fired on the main thread. */
public class ClanRoleChangeEvent extends ClanEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final ClanRole oldRole;
    private final ClanRole newRole;

    public ClanRoleChangeEvent(Clan clan, UUID player, ClanRole oldRole, ClanRole newRole) {
        super(clan);
        this.player = player;
        this.oldRole = oldRole;
        this.newRole = newRole;
    }

    /** The member. */
    public UUID player() {
        return player;
    }

    /** Role before. */
    public ClanRole oldRole() {
        return oldRole;
    }

    /** Role after. */
    public ClanRole newRole() {
        return newRole;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
