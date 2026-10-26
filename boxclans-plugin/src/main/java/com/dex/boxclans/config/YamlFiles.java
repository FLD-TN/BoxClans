package com.dex.boxclans.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads one YAML file of {@code plugins/BoxClans/}: the file is created from the jar copy when absent, and
 * the jar copy is attached as defaults, so a missing key always falls back to the bundled value (§8.1).
 */
public final class YamlFiles {

    private YamlFiles() {
    }

    /** Creates {@code path} from the jar if absent, then loads it with the bundled copy as defaults. */
    public static YamlConfiguration load(JavaPlugin plugin, String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists() && plugin.getResource(path) != null) plugin.saveResource(path, false);
        return load(file, plugin.getResource(path), plugin.getLogger());
    }

    /** Key of display-only files (messages, menus, icons) whose wording a release may replace as a whole. */
    public static final String TEXT_VERSION = "text-version";
    /** Old copies replaced by {@link #loadText} go here, under {@code v<old version>/}. */
    public static final String OLD_DIR = "old-configs";

    /**
     * Like {@link #load(JavaPlugin, String)} for a display-only file: when the jar copy has a higher
     * {@code text-version} than the server copy (a missing key counts as 1), the server copy is moved to
     * {@code old-configs/v<old>/<path>} and replaced by the jar copy, so a release that rewrites every text
     * reaches servers without the owner deleting files. The old copy is never deleted.
     */
    public static YamlConfiguration loadText(JavaPlugin plugin, String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (file.exists() && plugin.getResource(path) != null) upgradeText(plugin, file, path);
        return load(plugin, path);
    }

    private static void upgradeText(JavaPlugin plugin, File file, String path) {
        Logger logger = plugin.getLogger();
        int bundled = textVersion(readBundled(plugin.getResource(path), path, logger));
        YamlConfiguration current = new YamlConfiguration();
        try {
            current.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            return; // a broken file is reported by load() and left untouched
        }
        int version = textVersion(current);
        if (version >= bundled) return;
        String target = OLD_DIR + "/v" + version + "/" + path;
        File old = new File(plugin.getDataFolder(), target);
        // an earlier copy moved there is never overwritten
        if (old.exists()) {
            target = target + "." + System.currentTimeMillis();
            old = new File(plugin.getDataFolder(), target);
        }
        File parent = old.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("[" + path + "] could not create " + parent + ", the old texts stay in use");
            return;
        }
        try {
            java.nio.file.Files.move(file.toPath(), old.toPath());
        } catch (IOException ex) {
            logger.log(Level.WARNING, "[" + path + "] could not move the old copy, the old texts stay in use", ex);
            return;
        }
        logger.info("[" + path + "] texts updated (text-version " + version + " -> " + bundled + "), old copy: "
                + target);
    }

    private static int textVersion(YamlConfiguration yaml) {
        return yaml.get(TEXT_VERSION) instanceof Number n ? n.intValue() : 1;
    }

    /**
     * Loads {@code file} (may be missing) with {@code bundled} as defaults. An unreadable or invalid file is
     * logged and replaced by the bundled values only; it is never overwritten.
     */
    public static YamlConfiguration load(File file, InputStream bundled, Logger logger) {
        YamlConfiguration defaults = readBundled(bundled, file.getName(), logger);
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException ex) {
                logger.log(Level.SEVERE, "[" + file.getName() + "] cannot be read, using the bundled defaults "
                        + "until it is fixed: " + ex.getMessage());
                yaml = new YamlConfiguration();
            }
        }
        yaml.setDefaults(defaults);
        return yaml;
    }

    // ------------------------------------------------------------------ typed reads
    // Bukkit's getX(path, def) returns def instead of the attached defaults: these read the file value, then the
    // bundled value, then the fallback.

    public static boolean bool(ConfigurationSection s, String path, boolean fallback) {
        return s.get(path) instanceof Boolean b ? b : fallback;
    }

    public static String string(ConfigurationSection s, String path, String fallback) {
        Object v = s.get(path);
        return v == null || v instanceof ConfigurationSection || v instanceof java.util.List<?> ? fallback : String.valueOf(v);
    }

    public static double number(ConfigurationSection s, String path, double fallback) {
        return s.get(path) instanceof Number n ? n.doubleValue() : fallback;
    }

    public static long integer(ConfigurationSection s, String path, long fallback) {
        return s.get(path) instanceof Number n ? n.longValue() : fallback;
    }

    /** Parses a bundled resource; empty configuration when absent or broken. */
    public static YamlConfiguration readBundled(InputStream bundled, String name, Logger logger) {
        YamlConfiguration defaults = new YamlConfiguration();
        if (bundled == null) return defaults;
        try (Reader reader = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
            defaults.load(reader);
        } catch (IOException | InvalidConfigurationException ex) {
            logger.log(Level.SEVERE, "Bundled " + name + " is broken", ex);
        }
        return defaults;
    }
}
