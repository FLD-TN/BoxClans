package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.clan.InviteManager;
import com.dex.boxclans.clan.Outcome;
import com.dex.boxclans.clan.RolePermission;
import com.dex.boxclans.util.Names;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** {@code /clan} (§9). */
public final class ClanCommand implements TabExecutor {

    static final List<String> SUBCOMMANDS = List.of("create", "disband", "invite", "uninvite", "accept", "deny",
            "invites", "leave", "kick", "setrole", "promote", "demote", "transfer", "rename", "description", "flag",
            "pvp", "chat", "settings", "shop", "kho", "icons", "lichsu", "info", "list", "top", "help");

    private final BoxClansPlugin plugin;

    public ClanCommand(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    private ClanActions actions() {
        return plugin.actions();
    }

    private void usage(CommandSender to, String usage) {
        plugin.messages().send(to, "usage", "usage", usage);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boxclans.use")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        // console-friendly sub-commands
        switch (sub) {
            case "help" -> {
                plugin.messages().sendList(sender, "help");
                return true;
            }
            case "info" -> {
                info(sender, args, label);
                return true;
            }
            case "list" -> {
                if (sender instanceof Player p) plugin.guis().openList(p);
                else actions().sendList(sender, args.length > 1 ? parsePage(args[1]) : 1);
                return true;
            }
            case "top" -> {
                if (sender instanceof Player p) plugin.guis().openLeaderboard(p);
                else actions().sendTop(sender);
                return true;
            }
            default -> {
            }
        }
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        switch (sub) {
            case "" -> plugin.guis().openMain(p);
            case "create" -> actions().startCreate(p,
                    args.length < 2 ? null : String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            case "disband" -> actions().disband(p);
            case "invite" -> {
                if (args.length < 2) actions().startInvite(p);
                else actions().invite(p, args[1]);
            }
            case "uninvite" -> {
                if (args.length < 2) usage(p, "/" + label + " uninvite <người chơi>");
                else actions().uninvite(p, args[1]);
            }
            case "accept", "join" -> actions().accept(p, args.length > 1 ? args[1] : null);
            case "deny" -> actions().deny(p, args.length > 1 ? args[1] : null);
            case "invites" -> plugin.guis().openInvites(p);
            case "leave" -> actions().leave(p);
            case "kick" -> {
                if (args.length < 2) usage(p, "/" + label + " kick <người chơi>");
                else actions().kick(p, args[1]);
            }
            case "setrole" -> {
                ClanRole role = args.length < 3 ? null : ClanRole.byKey(args[2]).orElse(null);
                if (role == null) usage(p, "/" + label + " setrole <người chơi> <MinhChu|PhoBang|ThanhVien>");
                else actions().setRole(p, args[1], role);
            }
            case "promote" -> roleShortcut(p, args, label, ClanRole.VICE);
            case "demote" -> roleShortcut(p, args, label, ClanRole.MEMBER);
            case "transfer" -> roleShortcut(p, args, label, ClanRole.LEADER);
            case "rename" -> actions().startRename(p);
            case "description", "desc" -> {
                if (args.length < 2) actions().startDescription(p);
                else actions().setDescription(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            }
            case "flag" -> {
                ClanData clan = plugin.manager().of(p.getUniqueId());
                if (clan == null) actions().fail(p, Outcome.NOT_IN_CLAN);
                else if (!plugin.manager().can(clan, p.getUniqueId(), RolePermission.EDIT_FLAG)) {
                    actions().fail(p, Outcome.NO_PERMISSION);
                } else plugin.guis().openFlag(p);
            }
            case "pvp" -> actions().toggleFriendlyFire(p);
            case "chat" -> {
                if (args.length < 2) actions().toggleChatMode(p);
                else plugin.clanChat().send(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            }
            case "settings" -> {
                if (plugin.manager().of(p.getUniqueId()) == null) actions().fail(p, Outcome.NOT_IN_CLAN);
                else plugin.guis().openSettings(p);
            }
            case "shop" -> {
                if (plugin.manager().of(p.getUniqueId()) == null) actions().fail(p, Outcome.NOT_IN_CLAN);
                else plugin.guis().openShop(p);
            }
            case "kho", "vault" -> {
                if (plugin.manager().of(p.getUniqueId()) == null) actions().fail(p, Outcome.NOT_IN_CLAN);
                else plugin.guis().openVault(p);
            }
            case "lichsu", "history" -> {
                if (plugin.manager().of(p.getUniqueId()) == null) actions().fail(p, Outcome.NOT_IN_CLAN);
                else plugin.guis().openHistory(p);
            }
            case "icons", "icon" -> {
                if (plugin.manager().of(p.getUniqueId()) == null) actions().fail(p, Outcome.NOT_IN_CLAN);
                else plugin.guis().openIcons(p);
            }
            default -> plugin.messages().send(p, "unknown-command");
        }
        return true;
    }

    private void roleShortcut(Player p, String[] args, String label, ClanRole role) {
        if (args.length < 2) {
            usage(p, "/" + label + " " + args[0].toLowerCase(Locale.ROOT) + " <người chơi>");
            return;
        }
        actions().setRole(p, args[1], role);
    }

    private static int parsePage(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    /** {@code /clan info [clan|player]}: a clan key first, then a player name. */
    private void info(CommandSender sender, String[] args, String label) {
        ClanSnapshot clan;
        if (args.length < 2) {
            if (!(sender instanceof Player p)) {
                usage(sender, "/" + label + " info <mã lệnh|người chơi>");
                return;
            }
            clan = plugin.index().of(p.getUniqueId());
            if (clan == null) {
                actions().fail(p, Outcome.NOT_IN_CLAN);
                return;
            }
        } else {
            String arg = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).replace("\"", "").trim();
            clan = KeyArgs.clan(plugin, arg);
            if (clan == null) {
                OfflinePlayer target = Names.known(args[1]);
                if (target != null) clan = plugin.index().of(target.getUniqueId());
            }
            if (clan == null) {
                plugin.messages().send(sender, "clan-not-found", "clan", arg);
                return;
            }
        }
        if (sender instanceof Player p && args.length >= 2) plugin.guis().openPreview(p, clan);
        else actions().sendInfo(sender, clan);
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return KeyArgs.filter(SUBCOMMANDS, args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pl ? pl : null;
        ClanData clan = p == null ? null : plugin.manager().of(p.getUniqueId());
        if (args.length == 2) {
            return switch (sub) {
                case "invite" -> {
                    List<String> names = new ArrayList<>();
                    for (Player o : Bukkit.getOnlinePlayers()) {
                        if (plugin.index().of(o.getUniqueId()) == null && (p == null || p.canSee(o))) names.add(o.getName());
                    }
                    yield KeyArgs.filter(names, args[1]);
                }
                case "uninvite" -> {
                    List<String> names = new ArrayList<>();
                    if (clan != null) {
                        for (InviteManager.Invite i : plugin.manager().invites().ofClan(clan.uuid())) names.add(Names.of(i.target()));
                    }
                    yield KeyArgs.filter(names, args[1]);
                }
                case "kick", "setrole", "promote", "demote", "transfer" -> {
                    List<String> names = new ArrayList<>();
                    if (clan != null) {
                        for (ClanData.Member m : clan.members()) {
                            if (!m.player().equals(p.getUniqueId())) names.add(Names.of(m.player()));
                        }
                    }
                    yield KeyArgs.filter(names, args[1]);
                }
                case "accept", "deny" -> {
                    List<String> keys = new ArrayList<>();
                    if (p != null) {
                        for (InviteManager.Invite i : plugin.manager().invites().forPlayer(p.getUniqueId())) {
                            ClanData c = plugin.manager().clan(i.clan());
                            if (c != null) keys.add(c.key());
                        }
                    }
                    yield KeyArgs.filter(keys, args[1]);
                }
                case "info" -> {
                    List<String> all = new ArrayList<>(KeyArgs.keys(plugin));
                    for (Player o : Bukkit.getOnlinePlayers()) all.add(o.getName());
                    yield KeyArgs.filter(all, args[1]);
                }
                default -> List.of();
            };
        }
        if (args.length == 3 && sub.equals("setrole")) return KeyArgs.filter(KeyArgs.ROLE_KEYS, args[2]);
        return List.of();
    }
}
