package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** A clan chat message is about to be sent. Cancel to drop it, or change the message. Fired on the main thread. */
public class ClanChatEvent extends ClanEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID sender;
    private String message;
    private boolean cancelled;

    public ClanChatEvent(Clan clan, UUID sender, String message) {
        super(clan);
        this.sender = sender;
        this.message = message;
    }

    /** The sender. */
    public UUID sender() {
        return sender;
    }

    /** The message (plain text). */
    public String message() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
