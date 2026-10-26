package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.gui.ClanHistoryMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /clanadmin history <mã>}: staff read any clan's history (BOXCLANS_SHOP_SPEC.md §6.2). */
public final class HistoryCommand implements ClanAdminCommand.SubCommand {

    private final BoxClansPlugin plugin;

    public HistoryCommand(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "usage", "usage", "/" + label + " history <mã bang hội>");
            return;
        }
        ClanData clan = ClanTarget.resolve(plugin, sender, ClanTarget.CLAN, args[1]);
        if (clan != null) new ClanHistoryMenu(plugin, p, clan.uuid(), true).open();
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return args.length == 2 ? KeyArgs.filter(KeyArgs.keys(plugin), args[1]) : List.of();
    }
}
