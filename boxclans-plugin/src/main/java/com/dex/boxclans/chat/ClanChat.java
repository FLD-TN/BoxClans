package com.dex.boxclans.chat;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.api.event.ClanChatEvent;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.RolePermission;
import com.dex.boxclans.config.RoleRegistry;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Names;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Clan chat (§4B): {@code chat.clan-format} to the online members, {@code chat.spy-format} to staff spies and the
 * console. The message is inserted as plain text, so colour codes typed by the player have no effect.
 */
public final class ClanChat {

    private final BoxClansPlugin plugin;

    public ClanChat(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Renders a chat format. {@code %role_color%} is the role colour, {@code %role%} the role name,
     * {@code %player%} the sender, {@code %message%} the text (never interpreted), {@code %clan%} the clan name.
     */
    public static Component format(String format, RoleRegistry.RoleInfo role, String player, String message,
                                   Text.Mini clan) {
        return Text.parse(Text.fill(Text.legacyToMini(format),
                "role_color", new Text.Mini(role.colorMini()),
                "role", role.display(),
                "player", player,
                "message", message,
                "clan", clan));
    }

    /** Sends a clan chat message from a player (main thread). */
    public void send(Player sender, String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) return;
        ClanData clan = plugin.manager().of(sender.getUniqueId());
        if (clan == null) {
            plugin.messages().send(sender, "error-not-in-clan");
            return;
        }
        if (!sender.hasPermission("boxclans.chat")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (!plugin.manager().can(clan, sender.getUniqueId(), RolePermission.USE_CLAN_CHAT)) {
            plugin.messages().send(sender, "error-no-permission");
            return;
        }
        ClanChatEvent event = new ClanChatEvent(plugin.index().get(clan.uuid()), sender.getUniqueId(), message);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.message() == null || event.message().isBlank()) return;
        message = event.message();

        ClanRole role = clan.roleOf(sender.getUniqueId());
        RoleRegistry.RoleInfo info = plugin.roles().info(role);
        Text.Mini clanName = clan.name().placeholder();
        Component line = format(plugin.settings().clanChatFormat(), info, sender.getName(), message, clanName);
        Component spy = format(plugin.settings().spyFormat(), info, sender.getName(), message, clanName);

        List<Player> members = Names.online(clan);
        for (Player p : members) {
            p.sendMessage(line);
            if (!p.equals(sender) && plugin.playerSettings().get(p.getUniqueId()).chatSound()) {
                plugin.sounds().play(p, "chat-clan");
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!clan.isMember(p.getUniqueId()) && plugin.playerSettings().isSpy(p.getUniqueId())
                    && p.hasPermission("boxclans.spy")) {
                p.sendMessage(spy);
            }
        }
        Bukkit.getConsoleSender().sendMessage(spy);
    }
}
