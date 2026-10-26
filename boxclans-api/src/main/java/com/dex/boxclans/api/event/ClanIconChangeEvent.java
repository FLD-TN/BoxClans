package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * A clan is about to change its shield icon (shop or {@code /clanadmin icon set}). Fired on the main thread BEFORE
 * the change: {@link #clan()} still carries the old icon. Cancel it to forbid the change (e.g. a clan currency
 * plugin that could not charge the price); the canceller tells the player why.
 */
public class ClanIconChangeEvent extends ClanEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String oldIcon;
    private final String newIcon;
    private final UUID actor;
    private boolean cancelled;

    public ClanIconChangeEvent(Clan clan, String oldIcon, String newIcon, UUID actor) {
        super(clan);
        this.oldIcon = oldIcon;
        this.newIcon = newIcon;
        this.actor = actor;
    }

    /** Icon id before the change, empty when none. */
    public String oldIcon() {
        return oldIcon;
    }

    /** Icon id after the change, empty when the icon is removed. */
    public String newIcon() {
        return newIcon;
    }

    /** The Minh Chủ who chose it, {@code null} for an admin command. */
    public UUID actor() {
        return actor;
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
