package com.dex.boxclans.chat;

import com.dex.boxclans.text.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/** Captures prompt input from chat (never shown in public chat) and closes prompts on quit. */
public final class PromptListener implements Listener {

    private final Plugin plugin;
    private final ChatPrompt prompts;

    public PromptListener(Plugin plugin, ChatPrompt prompts) {
        this.plugin = plugin;
        this.prompts = prompts;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!prompts.isOpen(id)) return;
        event.setCancelled(true);
        String text = typed(event);
        Bukkit.getScheduler().runTask(plugin, () -> prompts.input(id, text));
    }

    /**
     * The exact text the player typed. {@code event.message()} is not used: chat plugins (Essentials, CMI,
     * ChatControl…) rewrite it before us, stripping or translating {@code &} codes, which made clan names lose
     * their colours. The signed message is what the client sent; {@code originalMessage()} is the fallback.
     */
    static String typed(AsyncChatEvent event) {
        try {
            if (event.signedMessage() != null) {
                String raw = event.signedMessage().message();
                if (raw != null) return raw;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // older API or unsigned chat: use the original component below
        }
        return Text.plain(event.originalMessage());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.close(event.getPlayer().getUniqueId(), ChatPrompt.End.QUIT);
    }
}
