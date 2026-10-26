package com.dex.boxclans.clan;

import com.dex.boxclans.config.Messages;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@link ClanManager.Notices} for the server: an online player gets the message now; for an offline player the
 * chat line is rendered (prefix and placeholders filled) and stored in {@code notices}, then shown at the next
 * login (§6).
 */
public final class NoticeService implements ClanManager.Notices {

    private final Plugin plugin;
    private final Database db;
    private final Supplier<Messages> messages;

    public NoticeService(Plugin plugin, Database db, Supplier<Messages> messages) {
        this.plugin = plugin;
        this.db = db;
        this.messages = messages;
    }

    @Override
    public void send(UUID player, String key, Object... placeholders) {
        Messages m = messages.get();
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            m.send(online, key, placeholders);
            return;
        }
        Messages.Entry entry = m.entry(key);
        if (entry == null || entry.chat() == null) return;
        String line = Text.fill((entry.prefixed() ? m.prefix() : "") + entry.chat(), placeholders);
        db.addNotice(player, line, System.currentTimeMillis());
    }

    /** Shows the stored notices of a player who just joined. */
    public void deliver(Player player) {
        UUID id = player.getUniqueId();
        db.takeNotices(id).thenAccept(lines -> {
            if (lines.isEmpty()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(id);
                if (p == null) return;
                messages.get().send(p, "notice-header");
                for (String line : lines) p.sendMessage(Text.parse(line));
            });
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Could not read notices of " + id + ": " + ex.getMessage());
            return null;
        });
    }
}
