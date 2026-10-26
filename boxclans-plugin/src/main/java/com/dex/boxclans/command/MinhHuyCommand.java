package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.api.CurrencyResult;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.currency.MinhHuyService;
import com.dex.boxclans.history.HistoryType;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /clanadmin minhhuy give|add|take|set|reset|log} (BOXCLANS_SHOP_SPEC.md §2.2): the target is always written
 * with its kind, {@code clan <mã>} or {@code player <tên>}. Console allowed; every change is kept in the clan history.
 */
public final class MinhHuyCommand implements ClanAdminCommand.SubCommand {

    private static final List<String> ACTIONS = List.of("give", "add", "take", "set", "reset", "log");
    private static final int LOG_PAGE = 10;
    private static final List<String> LOG_TYPES = List.of(HistoryType.MINHHUY_ADD.key(), HistoryType.MINHHUY_TAKE.key(),
            HistoryType.MINHHUY_SET.key(), HistoryType.MINHHUY_RESET.key(), HistoryType.SHOP_BUY.key());

    private final BoxClansPlugin plugin;

    public MinhHuyCommand(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    private MinhHuyService service() {
        return plugin.minhHuy();
    }

    private void usage(CommandSender to, String label) {
        plugin.messages().send(to, "usage", "usage", "/" + label + " minhhuy give|take|set <clan|player> <mã|tên> "
                + "<số> [lý do] [-s] | reset clan <mã> | reset all [confirm] | log <clan|player> <mã|tên> [trang]");
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
            case "give", "add" -> change(sender, label, args, by, silent, Action.GIVE);
            case "take" -> change(sender, label, args, by, silent, Action.TAKE);
            case "set" -> change(sender, label, args, by, true, Action.SET);
            case "reset" -> reset(sender, label, args, by);
            case "log" -> log(sender, label, args);
            default -> usage(sender, label);
        }
    }

    private enum Action { GIVE, TAKE, SET }

    /** {@code <action> <clan|player> <mã|tên> <số> [lý do...]}. */
    private void change(CommandSender sender, String label, String[] args, String by, boolean silent, Action action) {
        if (args.length < 5) {
            usage(sender, label);
            return;
        }
        ClanData clan = ClanTarget.resolve(plugin, sender, args[2], args[3]);
        if (clan == null) return;
        long amount = Numbers.parsePositive(args[4]);
        if (amount < 0 || (amount == 0 && action != Action.SET)) {
            plugin.messages().send(sender, "minhhuy-invalid-amount", "value", args[4]);
            return;
        }
        String reason = MinhHuyService.reason(String.join(" ", Arrays.copyOfRange(args, 5, args.length)),
                plugin.messages().text("minhhuy-default-reason"));
        CurrencyResult r = switch (action) {
            case GIVE -> service().give(clan, amount, reason, by, silent);
            case TAKE -> service().take(clan, amount, reason, by, silent);
            case SET -> service().set(clan, amount, reason, by);
        };
        Object[] ph = {"clan", clan.name().placeholder(), "amount", Numbers.grouped(amount),
                "balance", Numbers.grouped(clan.minhHuy()), "reason", reason};
        switch (r.status()) {
            case OK -> plugin.messages().send(sender, switch (action) {
                case GIVE -> "minhhuy-given";
                case TAKE -> "minhhuy-taken";
                case SET -> "minhhuy-set";
            }, ph);
            case NOT_ENOUGH -> plugin.messages().send(sender, "minhhuy-not-enough", ph);
            case LIMIT -> plugin.messages().send(sender, "minhhuy-limit", ph);
            default -> plugin.messages().send(sender, "minhhuy-invalid-amount", "value", args[4]);
        }
    }

    /** {@code reset clan <mã>} or {@code reset all [confirm]}. */
    private void reset(CommandSender sender, String label, String[] args, String by) {
        if (args.length >= 3 && args[2].equalsIgnoreCase("all")) {
            if (args.length < 4 || !args[3].equalsIgnoreCase("confirm")) {
                plugin.messages().send(sender, "minhhuy-reset-confirm", "count", plugin.manager().clans().size());
                return;
            }
            int n = 0;
            for (ClanData clan : new ArrayList<>(plugin.manager().clans())) {
                service().reset(clan, by);
                n++;
            }
            plugin.messages().send(sender, "minhhuy-reset-all", "count", n);
            return;
        }
        if (args.length < 4) {
            usage(sender, label);
            return;
        }
        ClanData clan = ClanTarget.resolve(plugin, sender, args[2], args[3]);
        if (clan == null) return;
        service().reset(clan, by);
        plugin.messages().send(sender, "minhhuy-reset", "clan", clan.name().placeholder());
    }

    /** {@code log <clan|player> <mã|tên> [trang]}: Minh Huy entries of the clan history, newest first. */
    private void log(CommandSender sender, String label, String[] args) {
        if (args.length < 4) {
            usage(sender, label);
            return;
        }
        ClanData clan = ClanTarget.resolve(plugin, sender, args[2], args[3]);
        if (clan == null) return;
        int page = 1;
        if (args.length > 4) {
            long n = Numbers.parsePositive(args[4]);
            if (n < 0) {
                plugin.messages().send(sender, "invalid-number", "value", args[4]);
                return;
            }
            page = (int) Math.max(1, Math.min(n, 100_000));
        }
        int pg = page;
        Text.Mini name = clan.name().placeholder();
        long balance = clan.minhHuy();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(plugin.pointsConfig().zone());
        plugin.database().historyPage(clan.uuid(), LOG_TYPES, (pg - 1) * LOG_PAGE, LOG_PAGE).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> showLog(sender, name, balance, pg, result, fmt)));
    }

    private void showLog(CommandSender sender, Text.Mini clan, long balance, int page, Database.HistoryPage result,
                         DateTimeFormatter fmt) {
        int pages = Math.max(1, (result.total() + LOG_PAGE - 1) / LOG_PAGE);
        plugin.messages().send(sender, "minhhuy-log-header", "clan", clan, "page", page, "pages", pages,
                "count", result.total(), "balance", Numbers.grouped(balance));
        if (result.rows().isEmpty()) {
            plugin.messages().send(sender, "minhhuy-log-empty");
            return;
        }
        for (Database.HistoryRow row : result.rows()) {
            HistoryType type = HistoryType.of(row.type());
            String change = type == null ? String.valueOf(row.amount()) : switch (type) {
                case MINHHUY_ADD -> "+" + Numbers.grouped(row.amount());
                case MINHHUY_TAKE, SHOP_BUY -> "-" + Numbers.grouped(row.amount());
                case MINHHUY_SET -> "= " + Numbers.grouped(row.amount());
                case MINHHUY_RESET -> "= 0";
                default -> String.valueOf(row.amount());
            };
            plugin.messages().send(sender, "minhhuy-log-entry",
                    "time", fmt.format(Instant.ofEpochMilli(row.at())),
                    "change", change,
                    "reason", row.detail().isEmpty() ? "-" : row.detail(),
                    "by", row.actorName().isEmpty() ? "-" : row.actorName());
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) return KeyArgs.filter(ACTIONS, args[1]);
        String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            if (action.equals("reset")) return KeyArgs.filter(List.of(ClanTarget.CLAN, "all"), args[2]);
            return ACTIONS.contains(action) ? KeyArgs.filter(ClanTarget.KINDS, args[2]) : List.of();
        }
        if (args.length == 4) {
            if (action.equals("reset") && args[2].equalsIgnoreCase("all")) return KeyArgs.filter(List.of("confirm"), args[3]);
            return ClanTarget.complete(plugin, args[2], args[3]);
        }
        if (args.length == 5 && !action.equals("log") && !action.equals("reset")) {
            return KeyArgs.filter(List.of("10", "100", "1000"), args[4]);
        }
        if (args.length >= 6 && !action.equals("set") && !action.equals("log") && !action.equals("reset")) {
            return KeyArgs.filter(List.of("-s"), args[args.length - 1]);
        }
        return List.of();
    }
}
