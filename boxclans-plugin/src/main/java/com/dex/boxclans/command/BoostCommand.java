package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.hook.ClanBoosts;
import com.dex.boxclans.shop.ShopActions;
import com.dex.boxclans.util.Durations;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code /clanadmin boost start|stop <clan|player> <mã|tên> <loại> ...} (BOXCLANS_SHOP_SPEC.md §5.6): staff start or
 * stop a clan boost of BoxCore without a bottle (event rewards). A start by staff replaces a lower boost at once.
 */
public final class BoostCommand implements ClanAdminCommand.SubCommand {

    private static final Pattern MULTIPLIER = Pattern.compile("^x?([0-9]{1,5})(?:[.,]([0-9]))?$");

    private final BoxClansPlugin plugin;

    public BoostCommand(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    private void usage(CommandSender to, String label) {
        plugin.messages().send(to, "usage", "usage", "/" + label + " boost start <clan|player> <mã|tên> <loại> <hệ số> "
                + "<thời gian> | stop <clan|player> <mã|tên> <loại>");
    }

    /** {@code 2} → 20, {@code 1.5} → 15, invalid → -1. */
    static int tenths(String raw) {
        Matcher m = MULTIPLIER.matcher(raw.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) return -1;
        return Integer.parseInt(m.group(1)) * 10 + (m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        if (args.length < 5) {
            usage(sender, label);
            return;
        }
        ClanBoosts boosts = plugin.clanBoosts();
        if (boosts == null) {
            plugin.messages().send(sender, "boost-unavailable");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        ClanData clan = ClanTarget.resolve(plugin, sender, args[2], args[3]);
        if (clan == null) return;
        String type = args[4].toLowerCase(Locale.ROOT);
        if (!boosts.isType(type)) {
            plugin.messages().send(sender, "boost-unknown-type", "type", type);
            return;
        }
        if (action.equals("stop")) {
            boolean stopped = boosts.stop(clan.uuid(), type);
            plugin.messages().send(sender, stopped ? "boost-stopped" : "boost-not-running", "clan", clan.name().placeholder(),
                    "type", type);
            return;
        }
        if (!action.equals("start") || args.length < 7) {
            usage(sender, label);
            return;
        }
        int tenths = tenths(args[5]);
        OptionalLong seconds = Durations.parseSeconds(args[6]);
        if (tenths <= 10 || seconds.isEmpty() || seconds.getAsLong() <= 0) {
            plugin.messages().send(sender, "boost-bad-value", "multiplier", args[5], "duration", args[6]);
            return;
        }
        ClanBoosts.Result r = boosts.start(clan.uuid(), type, tenths, seconds.getAsLong(), true, null);
        Object[] ph = {"clan", clan.name().placeholder(), "type", type, "multiplier", ShopActions.tenths(tenths),
                "old", ShopActions.tenths(r.oldTenths()), "duration", Durations.format(seconds.getAsLong(), plugin.messages())};
        String key = switch (r.status()) {
            case STARTED -> "boost-started";
            case EXTENDED -> "boost-extended";
            case REPLACED -> "boost-replaced";
            case LOWER -> "boost-lower";
            case FULL -> "boost-full";
            case INVALID -> "boost-bad-value";
            default -> "boost-unavailable";
        };
        plugin.messages().send(sender, key, ph);
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) return KeyArgs.filter(List.of("start", "stop"), args[1]);
        if (args.length == 3) return KeyArgs.filter(ClanTarget.KINDS, args[2]);
        if (args.length == 4) return ClanTarget.complete(plugin, args[2], args[3]);
        if (args.length == 5) return KeyArgs.filter(List.of("mining", "quest"), args[4]);
        if (args.length == 6 && args[1].equalsIgnoreCase("start")) return KeyArgs.filter(List.of("2", "1.5", "3"), args[5]);
        if (args.length == 7 && args[1].equalsIgnoreCase("start")) return KeyArgs.filter(List.of("30m", "1h", "2h"), args[6]);
        return List.of();
    }
}
