package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** Points are about to be added to a clan (§5.1). Cancel it, or change the amount (e.g. a weekend x2 event). Fired on the main thread. */
public class ClanPointsGainEvent extends ClanEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final String source;
    private long amount;
    private boolean cancelled;

    public ClanPointsGainEvent(Clan clan, UUID player, String source, long amount) {
        super(clan);
        this.player = player;
        this.source = source;
        this.amount = amount;
    }

    /** The player credited for the contribution, {@code null} for clan-wide awards. */
    public UUID player() {
        return player;
    }

    /** Source id from points.yml. */
    public String source() {
        return source;
    }

    /** Points to add (negative = loss). */
    public long amount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
