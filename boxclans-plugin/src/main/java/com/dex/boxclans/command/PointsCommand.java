package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.api.PointResult;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.points.PointSource;
import com.dex.boxclans.points.PointsService;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Names;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** {@code /clanadmin points give|add|take|set|reset|log} (§5.5); console allowed, every change logged. */
public final class PointsCommand implements ClanAdminCommand.SubCommand {

    private static final List<String> ACTIONS = List.of("give", "add", "take", "set", "reset", "log");
    private static final int LOG_PAGE = 10;

    private final BoxClansPlugin plugin;

    public PointsCommand(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    private PointsService points() {
        return plugin.points();
    }

    private void usage(CommandSender to, String label) {
        plugin.messages().send(to, "usage", "usage", "/" + label + " points give <người chơi> <nguồn> [số] [-s] | "
                + "add|take <mã> <số> [nguồn] [-s] | set <mã> <số> | reset <mã|all> [confirm] | log <mã> [trang]");
    }

    @Override
    public void run(CommandSender sender, String label, String[] all) {
        boolean silent = false;
        List<String> list = new ArrayList<>();
        for (String a : all) {
            if (a.equalsIgnoreCase("-s")) silent = true;
            else list.add(a);
        }
        String[] args = list.toArray(new String[0]);
        if (args.length < 2) {
            usage(sender, label);
            return;
        }
        String by = sender instanceof Player p ? p.getName() : "console";
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, label, args, by, silent);
            case "add", "take" -> addTake(sender, label, args, by, silent, args[1].equalsIgnoreCase("take"));
            case "set" -> set(sender, label, args, by);
            case "reset" -> reset(sender, label, args, by);
            case "log" -> log(sender, label, args);
            default -> usage(sender, label);
        }
    }

    private ClanData clan(CommandSender sender, String arg) {
        ClanSnapshot snap = KeyArgs.clan(plugin, arg);
        ClanData clan = snap == null ? null : plugin.manager().clan(snap.uuid());
        if (clan == null) plugin.messages().send(sender, "clan-not-found", "clan", arg);
        return clan;
    }

    private Long number(CommandSender sender, String arg) {
        try {
            return Long.parseLong(arg);
        } catch (NumberFormatException ex) {
            plugin.messages().send(sender, "invalid-number", "value", arg);
            return null;
        }
    }

    private Text.Mini display(String source) {
        PointSource src = points().config().source(source);
        return new Text.Mini(Text.legacyToMini(src == null ? source : src.display()));
    }

    /** {@code give <player> <source> [amount]}: the source's rules, credited to the player's clan. */
    private void give(CommandSender sender, String label, String[] args, String by, boolean silent) {
        if (args.length < 4) {
            usage(sender, label);
            return;
        }
        OfflinePlayer target = Names.known(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[2]);
            return;
        }
        Long amount = null;
        if (args.length > 4) {
            amount = number(sender, args[4]);
            if (amount == null) return;
        }
        String source = args[3].toLowerCase(Locale.ROOT);
        PointResult r = points().award(target.getUniqueId(), source, amount, by, silent);
        String name = target.getName() == null ? args[2] : target.getName();
        if (r.success()) {
            ClanData clan = plugin.manager().of(target.getUniqueId());
            plugin.messages().send(sender, "points-given", "amount", r.amount(), "source", display(source),
                    "player", name, "clan", clan.name().placeholder(), "total", r.total());
        } else {
            plugin.messages().send(sender, "points-not-given", "player", name, "source", display(source),
                    "reason", new Text.Mini(plugin.messages().text(
                            "point-status." + r.status().name().toLowerCase(Locale.ROOT).replace('_', '-'))));
        }
    }

    private void addTake(CommandSender sender, String label, String[] args, String by, boolean silent, boolean take) {
        if (args.length < 4) {
            usage(sender, label);
            return;
        }
        ClanData clan = clan(sender, args[2]);
        if (clan == null) return;
        Long n = number(sender, args[3]);
        if (n == null) return;
        String source = args.length > 4 ? args[4].toLowerCase(Locale.ROOT) : "admin";
        long delta = take ? -Math.abs(n) : n;
        PointResult r = points().adjust(clan, delta, source, by, silent);
        plugin.messages().send(sender, take ? "points-taken" : "points-added", "amount", Math.abs(r.amount()),
                "clan", clan.name().placeholder(), "source", display(source), "total", clan.points());
    }

    private void set(CommandSender sender, String label, String[] args, String by) {
        if (args.length < 4) {
            usage(sender, label);
            return;
        }
        ClanData clan = clan(sender, args[2]);
        if (clan == null) return;
        Long n = number(sender, args[3]);
        if (n == null) return;
        points().set(clan, n, "set", by);
        plugin.messages().send(sender, "points-set", "clan", clan.name().placeholder(), "total", clan.points());
    }

    private void reset(CommandSender sender, String label, String[] args, String by) {
        if (args.length < 3) {
            usage(sender, label);
            return;
        }
        if (args[2].equalsIgnoreCase("all")) {
            if (args.length < 4 || !args[3].equalsIgnoreCase("confirm")) {
                plugin.messages().send(sender, "points-reset-confirm", "count", plugin.manager().clans().size());
                return;
            }
            int n = 0;
            for (ClanData clan : new ArrayList<>(plugin.manager().clans())) {
                points().set(clan, 0, "reset", by);
                n++;
            }
            plugin.messages().send(sender, "points-reset-all", "count", n);
            return;
        }
        ClanData clan = clan(sender, args[2]);
        if (clan == null) return;
        points().set(clan, 0, "reset", by);
        plugin.messages().send(sender, "points-reset", "clan", clan.name().placeholder());
    }

    private void log(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            usage(sender, label);
            return;
        }
        ClanData clan = clan(sender, args[2]);
        if (clan == null) return;
        int page = 1;
        if (args.length > 3) {
            Long n = number(sender, args[3]);
            if (n == null) return;
            page = (int) Math.max(1, n);
        }
        int pg = page;
        Text.Mini name = clan.name().placeholder();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(points().config().zone());
        plugin.database().logPage(clan.uuid(), (pg - 1) * LOG_PAGE, LOG_PAGE).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> showLog(sender, name, pg, result, fmt)));
    }

    private void showLog(CommandSender sender, Text.Mini clan, int page, Database.LogPage result, DateTimeFormatter fmt) {
        int pages = Math.max(1, (result.total() + LOG_PAGE - 1) / LOG_PAGE);
        plugin.messages().send(sender, "points-log-header", "clan", clan, "page", page, "pages", pages,
                "count", result.total());
        if (result.rows().isEmpty()) {
            plugin.messages().send(sender, "points-log-empty");
            return;
        }
        for (Database.LogRow row : result.rows()) {
            plugin.messages().send(sender, "points-log-entry",
                    "time", fmt.format(Instant.ofEpochMilli(row.at())),
                    "source", display(row.source()),
                    "player", row.player() == null ? "-" : Names.of(row.player()),
                    "delta", (row.delta() > 0 ? "+" : "") + row.delta(),
                    "by", row.by());
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) return KeyArgs.filter(ACTIONS, args[1]);
        String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            if (action.equals("give")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return KeyArgs.filter(names, args[2]);
            }
            List<String> keys = new ArrayList<>(KeyArgs.keys(plugin));
            if (action.equals("reset")) keys.add("all");
            return KeyArgs.filter(keys, args[2]);
        }
        List<String> sources = new ArrayList<>(points().config().sources().keySet());
        if (args.length == 4 && action.equals("give")) return KeyArgs.filter(sources, args[3]);
        if (args.length == 5 && (action.equals("add") || action.equals("take"))) return KeyArgs.filter(sources, args[4]);
        if (args.length == 4 && action.equals("reset") && args[2].equalsIgnoreCase("all")) {
            return KeyArgs.filter(List.of("confirm"), args[3]);
        }
        if (args.length >= 4) return KeyArgs.filter(Arrays.asList("-s"), args[args.length - 1]);
        return List.of();
    }
}
