package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.HandlerList;

/** A clan changed its name (text or colour only). {@link #clan()} carries the new name. Fired on the main thread. */
public class ClanRenameEvent extends ClanEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String oldName;
    private final String oldKey;

    public ClanRenameEvent(Clan clan, String oldName, String oldKey) {
        super(clan);
        this.oldName = oldName;
        this.oldKey = oldKey;
    }

    /** True when only the colours changed (same command key, §4.2). */
    public boolean colorOnly() {
        return oldKey.equalsIgnoreCase(clan().key());
    }

    /** Previous coloured name, legacy {@code §} text. */
    public String oldName() {
        return oldName;
    }

    /** Previous command key. */
    public String oldKey() {
        return oldKey;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
