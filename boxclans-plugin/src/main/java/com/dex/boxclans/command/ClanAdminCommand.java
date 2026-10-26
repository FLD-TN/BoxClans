package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanManager;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.clan.Outcome;
import com.dex.boxclans.text.ClanNameFormatter;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Names;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** {@code /clanadmin} (§9): console allowed; {@code <clan>} is the command key. */
public final class ClanAdminCommand implements TabExecutor {

    /** Handles {@code points ...} and {@code killevent ...} (added by later phases). */
    public interface SubCommand {
        void run(CommandSender sender, String label, String[] args);

        List<String> complete(CommandSender sender, String[] args);
    }

    private static final List<String> SUBCOMMANDS = List.of("disband", "kick", "setrole", "rename", "trim",
            "points", "danhvong", "killevent", "icon", "spy", "reload", "help");

    private final BoxClansPlugin plugin;
    private final Map<String, SubCommand> extra = new HashMap<>();

    public ClanAdminCommand(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(String name, SubCommand sub) {
        extra.put(name, sub);
    }

    /** {@code points} and its Vietnamese name {@code danhvong} (Danh Vọng = clan points): one command, two names. */
    public void registerPoints(SubCommand points) {
        register("points", points);
        register("danhvong", points);
    }

    private void usage(CommandSender to, String usage) {
        plugin.messages().send(to, "usage", "usage", usage);
    }

    private ClanData clanArg(CommandSender sender, String arg) {
        ClanSnapshot snap = KeyArgs.clan(plugin, arg);
        ClanData clan = snap == null ? null : plugin.manager().clan(snap.uuid());
        if (clan == null) plugin.messages().send(sender, "clan-not-found", "clan", arg);
        return clan;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("spy")) {
            spy(sender);
            return true;
        }
        if (!sender.hasPermission("boxclans.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        SubCommand ext = extra.get(sub);
        if (ext != null) {
            ext.run(sender, label, args);
            return true;
        }
        switch (sub) {
            case "disband" -> disband(sender, label, args);
            case "kick" -> kick(sender, label, args);
            case "setrole" -> setRole(sender, label, args);
            case "rename" -> rename(sender, label, args);
            case "trim" -> trim(sender, label, args);
            case "reload" -> plugin.messages().send(sender, plugin.reload() ? "reload-done" : "reload-failed");
            default -> plugin.messages().sendList(sender, "admin-help");
        }
        return true;
    }

    private void spy(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!p.hasPermission("boxclans.spy")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        plugin.messages().send(p, plugin.playerSettings().toggleSpy(p.getUniqueId()) ? "spy-on" : "spy-off");
    }

    private void disband(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            usage(sender, "/" + label + " disband <mã lệnh>");
            return;
        }
        ClanData clan = clanArg(sender, args[1]);
        if (clan == null) return;
        Text.Mini name = clan.name().placeholder();
        plugin.manager().disband(clan, null);
        plugin.messages().send(sender, "admin-disbanded", "clan", name);
        plugin.messages().send(Bukkit.getOnlinePlayers(), "clan-disbanded-broadcast", "clan", name, "player", sender.getName());
    }

    private UUID playerArg(CommandSender sender, String name) {
        OfflinePlayer p = Names.known(name);
        if (p == null) {
            plugin.messages().send(sender, "player-not-found", "player", name);
            return null;
        }
        return p.getUniqueId();
    }

    private void kick(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            usage(sender, "/" + label + " kick <người chơi>");
            return;
        }
        UUID target = playerArg(sender, args[1]);
        if (target == null) return;
        ClanData clan = plugin.manager().of(target);
        Outcome o = plugin.manager().adminKick(target);
        if (o != Outcome.OK) {
            plugin.actions().fail(sender, o, "player", args[1]);
            return;
        }
        plugin.messages().send(sender, "admin-kicked", "player", Names.of(target), "clan", clan.name().placeholder());
        plugin.actions().toClan(clan, "member-kicked", "player", Names.of(target), "actor", sender.getName());
    }

    private void setRole(CommandSender sender, String label, String[] args) {
        ClanRole role = args.length < 3 ? null : ClanRole.byKey(args[2]).orElse(null);
        if (role == null) {
            usage(sender, "/" + label + " setrole <người chơi> <MinhChu|PhoBang|ThanhVien> [confirm]");
            return;
        }
        UUID target = playerArg(sender, args[1]);
        if (target == null) return;
        ClanData clan = plugin.manager().of(target);
        if (clan == null) {
            plugin.actions().fail(sender, Outcome.TARGET_NOT_MEMBER, "player", args[1]);
            return;
        }
        boolean confirm = args.length > 3 && args[3].equalsIgnoreCase("confirm");
        UUID oldLeader = clan.leader();
        if (role == ClanRole.LEADER && !confirm && clan.roleOf(target) != ClanRole.LEADER) {
            ClanManager.TransferPreview preview = plugin.manager().previewTransfer(clan, target);
            if (preview.excess() > 0) {
                plugin.messages().send(sender, "admin-transfer-confirm", "limit", preview.limit(), "excess", preview.excess());
                return;
            }
            confirm = true;
        }
        ClanManager.RoleResult r = plugin.manager().adminSetRole(target, role, confirm);
        if (r.outcome() == Outcome.NEEDS_CONFIRM) {
            plugin.messages().send(sender, "admin-swap-confirm", "player", Names.of(r.displaced()));
            return;
        }
        if (r.outcome() == Outcome.OK) {
            plugin.messages().send(sender, "admin-role-set", "player", Names.of(target),
                    "role", plugin.actions().role(role), "clan", clan.name().placeholder());
        }
        plugin.actions().announceRole(sender, clan, oldLeader, target, role, r);
    }

    private void rename(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            usage(sender, "/" + label + " rename <mã lệnh> <tên màu mới>");
            return;
        }
        ClanData clan = clanArg(sender, args[1]);
        if (clan == null) return;
        String raw = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        ClanNameFormatter.Check check = ClanNameFormatter.check(raw, plugin.settings(), perm -> true);
        if (!check.ok() && check.problem() != ClanNameFormatter.Problem.BANNED_WORD) {
            plugin.messages().send(sender, "name-" + check.problem().name().toLowerCase(Locale.ROOT).replace('_', '-'),
                    "min", plugin.settings().nameMinLength(), "max", plugin.settings().nameMaxLength(),
                    "detail", check.detail(), "word", check.detail(), "style", check.detail());
            return;
        }
        Text.Mini old = clan.name().placeholder();
        Outcome o = plugin.manager().adminRename(clan, check.name());
        if (o != Outcome.OK) {
            plugin.actions().fail(sender, o, "key", check.name().key());
            return;
        }
        plugin.messages().send(sender, "admin-renamed", "old", old, "clan", clan.name().placeholder(), "key", clan.key());
        plugin.actions().toClan(clan, "clan-renamed", "old", old, "clan", clan.name().placeholder(), "key", clan.key());
    }

    private void trim(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            usage(sender, "/" + label + " trim <mã lệnh>");
            return;
        }
        ClanData clan = clanArg(sender, args[1]);
        if (clan == null) return;
        List<UUID> kicked = plugin.manager().trim(clan);
        String limit = clan.maxMembers() < 0 ? plugin.messages().text("unlimited") : String.valueOf(clan.maxMembers());
        if (kicked.isEmpty()) {
            plugin.messages().send(sender, "admin-trim-none", "clan", clan.name().placeholder(), "size", clan.size(),
                    "limit", limit);
            return;
        }
        String names = String.join(", ", Names.of(kicked));
        plugin.messages().send(sender, "admin-trimmed", "count", kicked.size(), "clan", clan.name().placeholder(),
                "limit", limit, "players", names);
        plugin.actions().toClan(clan, "over-limit-kicked", "limit", limit, "players", names);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("boxclans.admin")) {
            return args.length == 1 && sender.hasPermission("boxclans.spy") ? KeyArgs.filter(List.of("spy"), args[0]) : List.of();
        }
        if (args.length == 1) return KeyArgs.filter(SUBCOMMANDS, args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT);
        SubCommand ext = extra.get(sub);
        if (ext != null) return ext.complete(sender, args);
        if (args.length == 2) {
            return switch (sub) {
                case "disband", "rename", "trim" -> KeyArgs.filter(KeyArgs.keys(plugin), args[1]);
                case "kick", "setrole" -> {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) if (plugin.index().of(p.getUniqueId()) != null) names.add(p.getName());
                    yield KeyArgs.filter(names, args[1]);
                }
                default -> List.of();
            };
        }
        if (args.length == 3 && sub.equals("setrole")) return KeyArgs.filter(KeyArgs.ROLE_KEYS, args[2]);
        if (args.length == 4 && sub.equals("setrole")) return KeyArgs.filter(List.of("confirm"), args[3]);
        return List.of();
    }
}
