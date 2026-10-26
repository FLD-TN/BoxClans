package com.dex.boxclans.api.event;

import com.dex.boxclans.api.Clan;
import org.bukkit.event.Event;

/** Base of the clan events: {@link #clan()} is the snapshot after the change (the last one for a disband). */
public abstract class ClanEvent extends Event {

    private final Clan clan;

    protected ClanEvent(Clan clan) {
        this.clan = clan;
    }

    public Clan clan() {
        return clan;
    }
}
