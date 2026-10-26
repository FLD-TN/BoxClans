package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** Points were added to a clan (after {@link ClanPointsGainEvent}). Fired on the main thread. */
public class ClanPointsAwardedEvent extends ClanEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final String source;
    private final long amount;
    private final long total;

    public ClanPointsAwardedEvent(Clan clan, UUID player, String source, long amount, long total) {
        super(clan);
        this.player = player;
        this.source = source;
        this.amount = amount;
        this.total = total;
    }

    /** The credited player, {@code null} for clan-wide awards. */
    public UUID player() {
        return player;
    }

    /** Source id. */
    public String source() {
        return source;
    }

    /** Points applied (negative = loss). */
    public long amount() {
        return amount;
    }

    /** Clan points after the change. */
    public long total() {
        return total;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
