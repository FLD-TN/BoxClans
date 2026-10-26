package com.dex.boxclans.listener;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.points.KillProcessor;
import com.dex.boxclans.points.KillRules;
import com.dex.boxclans.util.Durations;
import com.dex.boxclans.util.Names;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * {@link KillProcessor.Feedback} for the server (§7): one warning line for the killer, {@code anti-alt-case1/2}
 * for staff with {@code boxclans.admin.antialt} (rules A / B only), every block appended to {@code antialt.log}
 * on a background thread.
 */
public final class KillFeedback implements KillProcessor.Feedback, AutoCloseable {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BoxClansPlugin plugin;
    private final File logFile;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BoxClans-AntiAlt-Log");
        t.setDaemon(true);
        return t;
    });

    public KillFeedback(BoxClansPlugin plugin, File logFile) {
        this.plugin = plugin;
        this.logFile = logFile;
    }

    @Override
    public void blocked(UUID killer, UUID victim, KillRules.Verdict verdict) {
        Player p = Bukkit.getPlayer(killer);
        if (p == null) return;
        String key = switch (verdict) {
            case SAME_IP -> "kill-blocked-same-ip";
            case NETWORK -> "kill-blocked-network";
            case PAIR_LIMIT -> "kill-blocked-pair";
            case ALLOW -> null;
        };
        if (key == null) return;
        var af = plugin.settings().antiFarm();
        plugin.messages().send(p, key, "victim", Names.of(victim), "max", af.pairMaxKills(),
                "window", Durations.format(af.pairWindow(), plugin.messages()));
    }

    @Override
    public void clanCap(UUID killer, long cap) {
        Player p = Bukkit.getPlayer(killer);
        if (p != null) plugin.messages().send(p, "kill-blocked-clan-cap", "cap", cap);
    }

    @Override
    public void altSuspect(UUID killer, String killerIp, UUID victim, String victimIp, KillRules.Verdict verdict) {
        if (!plugin.settings().antiFarm().notifyStaff()) return;
        String key = verdict == KillRules.Verdict.SAME_IP ? "anti-alt-case1" : "anti-alt-case2";
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("boxclans.admin.antialt")) {
                plugin.messages().send(staff, key, "killer", Names.of(killer), "victim", Names.of(victim),
                        "ip", victimIp == null ? "?" : victimIp);
            }
        }
    }

    @Override
    public void log(UUID killer, String killerIp, UUID victim, String victimIp, KillRules.Verdict verdict) {
        if (!plugin.settings().antiFarm().logFile()) return;
        String line = LocalDateTime.now().format(TIME) + " " + rule(verdict) + " killer=" + Names.of(killer)
                + " (" + killer + ", " + killerIp + ") victim=" + Names.of(victim) + " (" + victim + ", " + victimIp + ")";
        writer.execute(() -> {
            try {
                File parent = logFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (BufferedWriter w = Files.newBufferedWriter(logFile.toPath(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    w.write(line);
                    w.newLine();
                }
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not write " + logFile.getName(), ex);
            }
        });
    }

    private static String rule(KillRules.Verdict v) {
        return switch (v) {
            case SAME_IP -> "[A same-ip]";
            case NETWORK -> "[B network]";
            case PAIR_LIMIT -> "[C pair-limit]";
            case ALLOW -> "[allow]";
        };
    }

    @Override
    public void close() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
