package com.dex.boxclans.hook;

import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Listens for LuckPerms permission recalculations so that a clan leader's member limit is refreshed
 * immediately when their permissions change (e.g. rank purchase, LP command, LP GUI edit).
 * <p>
 * This class is loaded <b>only</b> when LuckPerms is installed; the main plugin class never imports it
 * directly and guards the call with {@code isPluginEnabled("LuckPerms")}.
 */
public final class LuckPermsHook {

    private final EventSubscription<?> subscription;

    private LuckPermsHook(EventSubscription<?> subscription) {
        this.subscription = subscription;
    }

    /**
     * Subscribes to {@link UserDataRecalculateEvent}. When fired for a clan leader, the clan's member
     * limit is re-read on the main thread.
     *
     * @return the hook, or {@code null} when the LuckPerms service is unavailable
     */
    public static LuckPermsHook register(Plugin plugin, ClanManager manager) {
        LuckPerms lp;
        try {
            lp = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            // LuckPerms is not loaded yet or unavailable
            return null;
        }

        EventSubscription<UserDataRecalculateEvent> sub = lp.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            UUID uuid = event.getUser().getUniqueId();
            // All ClanManager access must happen on the main thread (non-thread-safe maps).
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ClanData clan = manager.of(uuid);
                if (clan != null && clan.leader().equals(uuid)) {
                    manager.refreshLimit(clan);
                }
            });
        });

        return new LuckPermsHook(sub);
    }

    /** Unsubscribes the listener. Called on plugin disable. */
    public void close() {
        subscription.close();
    }
}
