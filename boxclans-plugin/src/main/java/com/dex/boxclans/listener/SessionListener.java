package com.dex.boxclans.listener;

import com.dex.boxclans.clan.ClanManager;
import com.dex.boxclans.clan.NoticeService;
import com.dex.boxclans.points.PointsService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Join: last seen, re-read a leader's member limit (§6), show stored notices.
 * Quit: last seen, drop the player's point counts.
 */
public final class SessionListener implements Listener {

    private final ClanManager manager;
    private final NoticeService notices;
    private final PointsService points;

    public SessionListener(ClanManager manager, NoticeService notices, PointsService points) {
        this.manager = manager;
        this.notices = notices;
        this.points = points;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.onJoin(event.getPlayer().getUniqueId());
        notices.deliver(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.seen(event.getPlayer().getUniqueId());
        points.forget(event.getPlayer().getUniqueId());
    }
}
