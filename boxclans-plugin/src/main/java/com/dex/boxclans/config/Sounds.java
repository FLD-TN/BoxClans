package com.dex.boxclans.config;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Immutable snapshot of {@code sounds.yml}: {@code <key>: {enabled, sound, volume, pitch}} with the same keys as
 * the original plugin ({@code ui-click}, {@code error}, {@code success}…). {@code sound} is a Bukkit sound name
 * ({@code UI_BUTTON_CLICK}) or a resource key ({@code minecraft:ui.button.click}).
 */
public final class Sounds {

    public static final String FILE = "sounds.yml";

    public record Spec(boolean enabled, String sound, float volume, float pitch) {}

    private final Map<String, Spec> specs;
    private final Logger logger;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    private Sounds(Map<String, Spec> specs, Logger logger) {
        this.specs = specs;
        this.logger = logger;
    }

    public static Sounds from(YamlConfiguration yaml, Logger logger) {
        Set<String> keys = new TreeSet<>(yaml.getKeys(false));
        if (yaml.getDefaults() != null) keys.addAll(yaml.getDefaults().getKeys(false));
        Map<String, Spec> specs = new HashMap<>();
        for (String key : keys) {
            ConfigurationSection s = yaml.getConfigurationSection(key);
            if (s == null) continue;
            specs.put(key, new Spec(YamlFiles.bool(s, "enabled", true), YamlFiles.string(s, "sound", ""),
                    (float) YamlFiles.number(s, "volume", 1.0), (float) YamlFiles.number(s, "pitch", 1.0)));
        }
        return new Sounds(Map.copyOf(specs), logger);
    }

    public Spec spec(String key) {
        return specs.get(key);
    }

    /** Plays a sound to one player; unknown, disabled or invalid sounds are silently skipped. */
    public void play(Player player, String key) {
        if (player == null) return;
        Spec spec = specs.get(key);
        if (spec == null || !spec.enabled() || spec.sound().isEmpty()) return;
        String name = spec.sound();
        if (name.indexOf(':') >= 0 || name.indexOf('.') >= 0) {
            player.playSound(player.getLocation(), name.toLowerCase(Locale.ROOT), spec.volume(), spec.pitch());
            return;
        }
        try {
            Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, spec.volume(), spec.pitch());
        } catch (IllegalArgumentException ex) {
            if (reported.add(key)) logger.warning("[" + FILE + "] Unknown sound '" + name + "' for '" + key + "'");
        }
    }

    public void play(Collection<? extends Player> players, String key) {
        for (Player p : players) play(p, key);
    }
}
