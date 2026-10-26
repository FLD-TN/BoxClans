package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /cc [message]}: one clan chat message, or toggles clan chat mode without a message (§4B). */
public final class ClanChatCommand implements TabExecutor {

    private final BoxClansPlugin plugin;

    public ClanChatCommand(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!p.hasPermission("boxclans.chat")) {
            plugin.messages().send(p, "no-permission");
            return true;
        }
        if (args.length == 0) plugin.actions().toggleChatMode(p);
        else plugin.clanChat().send(p, String.join(" ", args));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
