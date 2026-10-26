package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.clan.Outcome;
import com.dex.boxclans.config.Icons;
import com.dex.boxclans.text.Text;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /clanadmin icon set <clan> <icon|none> | unlock <clan> <icon> | lock <clan> <icon> | list [clan]}
 * ({@code BOXCLANS_ICON_SPEC.md} §7). Console allowed.
 */
public final class IconCommand implements ClanAdminCommand.SubCommand {

    private static final List<String> ACTIONS = List.of("set", "unlock", "lock", "list");

    private final BoxClansPlugin plugin;

    public IconCommand(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    private void usage(CommandSender to, String label) {
        plugin.messages().send(to, "usage", "usage",
                "/" + label + " icon set <mã> <icon|none> | unlock <mã> <icon> | lock <mã> <icon> | list [mã]");
    }

    private ClanData clanArg(CommandSender sender, String arg) {
        ClanSnapshot snap = KeyArgs.clan(plugin, arg);
        ClanData clan = snap == null ? null : plugin.manager().clan(snap.uuid());
        if (clan == null) plugin.messages().send(sender, "clan-not-found", "clan", arg);
        return clan;
    }

    private Icons.Icon iconArg(CommandSender sender, String arg) {
        Icons.Icon icon = plugin.icons().icon(arg);
        if (icon == null) {
            plugin.messages().send(sender, "icon-unknown", "icon", arg,
                    "icons", String.join(", ", plugin.icons().ids()));
        }
        return icon;
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        String action = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> set(sender, label, args);
            case "unlock", "lock" -> ownership(sender, label, args, action.equals("unlock"));
            case "list" -> list(sender, args);
            default -> usage(sender, label);
        }
    }

    private void set(CommandSender sender, String label, String[] args) {
        if (args.length < 4) {
            usage(sender, label);
            return;
        }
        ClanData clan = clanArg(sender, args[2]);
        if (clan == null) return;
        boolean remove = args[3].equalsIgnoreCase("none");
        Icons.Icon icon = remove ? null : iconArg(sender, args[3]);
        if (!remove && icon == null) return;
        Outcome o = plugin.manager().adminSetIcon(clan, remove ? "" : icon.id());
        if (o != Outcome.OK) {
            plugin.messages().send(sender, "admin-icon-cancelled", "clan", clan.name().placeholder());
            return;
        }
        if (remove) {
            plugin.messages().send(sender, "admin-icon-removed", "clan", clan.name().placeholder());
        } else {
            plugin.messages().send(sender, "admin-icon-set", "clan", clan.name().placeholder(),
                    "icon", plugin.actions().iconName(icon));
        }
    }

    private void ownership(CommandSender sender, String label, String[] args, boolean unlock) {
        if (args.length < 4) {
            usage(sender, label);
            return;
        }
        ClanData clan = clanArg(sender, args[2]);
        if (clan == null) return;
        Icons.Icon icon = iconArg(sender, args[3]);
        if (icon == null) return;
        Object[] ph = {"clan", clan.name().placeholder(), "icon", plugin.actions().iconName(icon)};
        if (unlock) {
            plugin.messages().send(sender, plugin.manager().unlockIcon(clan, icon.id())
                    ? "admin-icon-unlocked" : "admin-icon-already-owned", ph);
        } else {
            plugin.messages().send(sender, plugin.manager().lockIcon(clan, icon.id())
                    ? "admin-icon-locked" : "admin-icon-not-owned", ph);
        }
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length < 3) {
            List<String> ids = new ArrayList<>();
            for (Icons.Icon icon : plugin.icons().all()) {
                ids.add(icon.id() + (icon.price() > 0 ? " (" + icon.price() + ")" : ""));
            }
            plugin.messages().send(sender, "admin-icon-list", "icons", String.join(", ", ids));
            return;
        }
        ClanData clan = clanArg(sender, args[2]);
        if (clan == null) return;
        Icons.Icon current = plugin.icons().icon(clan.icon());
        Text.Mini none = new Text.Mini(plugin.messages().text("none"));
        plugin.messages().send(sender, "admin-icon-list-clan", "clan", clan.name().placeholder(),
                "icon", current == null ? none : plugin.actions().iconName(current),
                "owned", clan.ownedIcons().isEmpty() ? none : String.join(", ", clan.ownedIcons()));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) return KeyArgs.filter(ACTIONS, args[1]);
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) return List.of();
        if (args.length == 3) return KeyArgs.filter(KeyArgs.keys(plugin), args[2]);
        if (args.length == 4 && !action.equals("list")) {
            List<String> ids = new ArrayList<>(plugin.icons().ids());
            if (action.equals("set")) ids.add("none");
            return KeyArgs.filter(ids, args[3]);
        }
        return List.of();
    }
}
