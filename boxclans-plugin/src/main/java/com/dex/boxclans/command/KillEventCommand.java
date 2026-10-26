package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.points.KillEventManager;
import com.dex.boxclans.points.PointsConfig;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Durations;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/** {@code /clanadmin killevent start <profile> <time> | stop | status} (§5.4). */
public final class KillEventCommand implements ClanAdminCommand.SubCommand {

    private final BoxClansPlugin plugin;

    public KillEventCommand(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        String action = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        KillEventManager events = plugin.killEvents();
        switch (action) {
            case "start" -> {
                if (args.length < 4) {
                    plugin.messages().send(sender, "usage", "usage", "/" + label + " killevent start <profile> <thời gian, ví dụ 30m>");
                    return;
                }
                PointsConfig.KillEventProfile profile = plugin.pointsConfig().killEvent(args[2]);
                if (profile == null) {
                    plugin.messages().send(sender, "killevent-unknown", "profile", args[2],
                            "profiles", String.join(", ", plugin.pointsConfig().killEvents().keySet()));
                    return;
                }
                OptionalLong seconds = Durations.parseSeconds(args[3]);
                if (seconds.isEmpty()) {
                    plugin.messages().send(sender, "invalid-number", "value", args[3]);
                    return;
                }
                if (!events.start(profile.id(), Duration.ofSeconds(seconds.getAsLong()))) {
                    plugin.messages().send(sender, "killevent-running", "event", display(events.current()));
                    return;
                }
                plugin.messages().send(sender, "killevent-started", "event", display(events.current()),
                        "time", Durations.format(seconds.getAsLong(), plugin.messages()),
                        "kill", profile.kill(), "death", profile.death());
            }
            case "stop" -> {
                KillEventManager.Active a = events.current();
                if (a == null) {
                    plugin.messages().send(sender, "killevent-none");
                    return;
                }
                events.stop();
                plugin.messages().send(sender, "killevent-stopped", "event", display(a));
            }
            default -> {
                KillEventManager.Active a = events.current();
                if (a == null) {
                    plugin.messages().send(sender, "killevent-none");
                    return;
                }
                plugin.messages().send(sender, "killevent-status", "event", display(a),
                        "time", Durations.format((a.endsAt() - System.currentTimeMillis()) / 1000, plugin.messages()));
            }
        }
    }

    private static Text.Mini display(KillEventManager.Active a) {
        return new Text.Mini(a == null ? "" : Text.legacyToMini(a.profile().display()));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) return KeyArgs.filter(List.of("start", "stop", "status"), args[1]);
        if (args.length == 3 && args[1].equalsIgnoreCase("start")) {
            return KeyArgs.filter(new ArrayList<>(plugin.pointsConfig().killEvents().keySet()), args[2]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("start")) return KeyArgs.filter(List.of("10m", "30m", "1h"), args[3]);
        return List.of();
    }
}
