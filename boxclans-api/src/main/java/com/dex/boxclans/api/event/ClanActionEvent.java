package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/**
 * A player is about to change clan membership (e.g. a KOTH plugin locks clans while a match runs). Fired on the
 * main thread BEFORE the change, both when the action starts and again right before it runs (after the chat
 * prompt / confirmation screen). Cancel it to forbid the action; the canceller tells the player why, BoxClans
 * only plays the error sound. Not fired for admin commands ({@code /clanadmin}).
 */
public class ClanActionEvent extends Event implements Cancellable {

    public enum Action {
        /** {@link #clan()} is {@code null}. */
        CREATE,
        DISBAND,
        LEAVE,
        /** Accepting an invite: {@link #clan()} is the clan being joined. */
        JOIN,
        /** {@link #target()} is the member being kicked. */
        KICK,
        /** {@link #target()} is the invited player, {@code null} while the chat prompt opens. */
        INVITE,
        /** The Minh Chủ hands the clan over: {@link #target()} is the new Minh Chủ. */
        TRANSFER
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Action action;
    private final Clan clan;
    private final UUID target;
    private boolean cancelled;

    public ClanActionEvent(Player player, Action action, Clan clan, UUID target) {
        this.player = player;
        this.action = action;
        this.clan = clan;
        this.target = target;
    }

    /** The player doing the action. */
    public Player player() {
        return player;
    }

    public Action action() {
        return action;
    }

    /** The clan acted on, {@code null} for {@link Action#CREATE}. */
    public Clan clan() {
        return clan;
    }

    /** The other player ({@link Action#KICK}, {@link Action#INVITE}, {@link Action#TRANSFER}), otherwise {@code null}. */
    public UUID target() {
        return target;
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
