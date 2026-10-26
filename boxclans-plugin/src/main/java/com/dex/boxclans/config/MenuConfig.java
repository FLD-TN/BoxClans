package com.dex.boxclans.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every {@code menus/*.yml} (§8.4): title, rows, layout, items, lore, material, actions. Each file is kept as a
 * YAML tree with the jar copy attached as defaults, so a key the owner deleted falls back to the bundled value.
 * The menus read their own keys; nothing here is hard-coded text.
 */
public final class MenuConfig {

    public static final String DIR = "menus/";
    /** Bundled menu files (§8.4). */
    public static final List<String> MENUS = List.of(
            "clan-detail", "clan-list", "clan-preview", "invites", "leaderboard", "settings", "flag-maker", "confirm",
            "clan-shop", "shop-entry", "clan-vault", "clan-icons", "clan-history");

    private final Map<String, YamlConfiguration> menus;

    private MenuConfig(Map<String, YamlConfiguration> menus) {
        this.menus = menus;
    }

    public static MenuConfig load(JavaPlugin plugin) {
        Map<String, YamlConfiguration> menus = new HashMap<>();
        for (String name : MENUS) {
            String path = DIR + name + ".yml";
            if (plugin.getResource(path) == null) continue;
            menus.put(name, YamlFiles.loadText(plugin, path));
        }
        return new MenuConfig(Map.copyOf(menus));
    }

    public static MenuConfig of(Map<String, YamlConfiguration> menus) {
        return new MenuConfig(Map.copyOf(menus));
    }

    /** The menu file, or an empty configuration when it does not exist. */
    public YamlConfiguration menu(String name) {
        YamlConfiguration yaml = menus.get(name);
        return yaml != null ? yaml : new YamlConfiguration();
    }

    public boolean has(String name) {
        return menus.containsKey(name);
    }
}
