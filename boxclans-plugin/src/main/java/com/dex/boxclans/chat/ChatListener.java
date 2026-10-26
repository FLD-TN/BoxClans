package com.dex.boxclans.chat;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.text.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Public chat → clan chat (§4B): a line starting with {@code chat.shortcut-prefix} ({@code !}), or any line while
 * chat mode is on. Runs on the async chat thread: only thread-safe reads here, the sending is moved to the main
 * thread. Prompt input is handled earlier ({@link PromptListener}, LOWEST) and arrives cancelled.
 */
public final class ChatListener implements Listener {

    private final BoxClansPlugin plugin;

    public ChatListener(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (plugin.index().of(id) == null) return;
        String text = Text.plain(event.message());
        String shortcut = plugin.settings().chatShortcut();
        String message;
        if (!shortcut.isEmpty() && text.startsWith(shortcut) && text.length() > shortcut.length()) {
            message = text.substring(shortcut.length());
        } else if (plugin.playerSettings().get(id).chatMode()) {
            message = text;
        } else {
            return;
        }
        if (message.isBlank()) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.clanChat().send(p, message);
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.playerSettings().load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.playerSettings().forget(event.getPlayer().getUniqueId());
    }
}
