package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.HandlerList;

/**
 * A clan's Minh Huy balance changed (given, taken, set, spent). Fired on the main thread after the change; it cannot
 * be cancelled.
 *
 * @since 1.2.0
 */
public class ClanMinhHuyChangeEvent extends ClanEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final long delta;
    private final long balance;
    private final String reason;

    public ClanMinhHuyChangeEvent(Clan clan, long delta, long balance, String reason) {
        super(clan);
        this.delta = delta;
        this.balance = balance;
        this.reason = reason;
    }

    /** Change of the balance (negative = removed). */
    public long delta() {
        return delta;
    }

    /** Balance after the change. */
    public long balance() {
        return balance;
    }

    /** Why it changed ("KOTH top 1", "Admin", a shop item...). */
    public String reason() {
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
