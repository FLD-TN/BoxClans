package com.dex.boxclans.points;

import com.dex.boxclans.config.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Brings the server's {@code points.yml} up to the bundled {@code config-version} without replacing it: sources
 * added by a release are copied as text (with their comments) to the end of {@code sources:}, everything else of
 * the file stays as the owner wrote it. A source the owner deleted after the upgrade is not added again. The file
 * before the upgrade is copied to {@code old-configs/v<old>/points.yml}.
 */
public final class PointsMigration {

    /** Sources added in each config-version (copied from the bundled file). */
    static final Map<Integer, List<String>> ADDED = Map.of(
            2, List.of("mob-boss", "mob-kill"));

    private static final Pattern VERSION_LINE = Pattern.compile("^config-version:.*$");

    private PointsMigration() {
    }

    public static void upgrade(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), PointsConfig.FILE);
        if (!file.exists()) return; // created from the jar copy, already current
        try (InputStream in = plugin.getResource(PointsConfig.FILE)) {
            if (in == null) return;
            String bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            upgrade(file, bundled, new File(plugin.getDataFolder(), YamlFiles.OLD_DIR), plugin.getLogger());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "[" + PointsConfig.FILE + "] upgrade skipped", ex);
        }
    }

    /** Returns true when the file was changed. */
    static boolean upgrade(File file, String bundledText, File oldDir, Logger logger) throws IOException {
        int target = version(parse(bundledText));
        String original = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        YamlConfiguration current = parse(original);
        if (current == null) return false; // broken file: reported by the normal load, left untouched
        int version = version(current);
        if (version >= target) return false;

        String nl = original.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(List.of(original.replace("\r\n", "\n").split("\n", -1)));
        List<String> bundledLines = List.of(bundledText.replace("\r\n", "\n").split("\n", -1));

        Set<String> missing = new LinkedHashSet<>();
        ConfigurationSection own = current.getConfigurationSection("sources");
        // a file without its own sources uses the bundled ones: nothing to copy
        if (own != null && !own.getKeys(false).isEmpty()) {
            Set<String> have = new LinkedHashSet<>();
            for (String id : own.getKeys(false)) have.add(id.toLowerCase(Locale.ROOT));
            for (int v = version + 1; v <= target; v++) {
                for (String id : ADDED.getOrDefault(v, List.of())) if (!have.contains(id)) missing.add(id);
            }
        }

        if (!missing.isEmpty()) {
            int end = endOfSources(lines);
            if (end < 0) {
                logger.warning("[" + PointsConfig.FILE + "] could not find the end of 'sources:', add the sources "
                        + missing + " by hand");
                return false;
            }
            List<String> block = new ArrayList<>();
            for (String id : missing) block.addAll(sourceBlock(bundledLines, id));
            lines.addAll(end + 1, block);
        }
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (VERSION_LINE.matcher(lines.get(i)).matches()) {
                lines.set(i, "config-version: " + target);
                replaced = true;
                break;
            }
        }
        if (!replaced) lines.add(0, "config-version: " + target);

        String upgraded = String.join(nl, lines);
        if (parse(upgraded) == null) {
            logger.warning("[" + PointsConfig.FILE + "] upgrade to config-version " + target
                    + " skipped (result would not be valid YAML), add the sources " + missing + " by hand");
            return false;
        }
        File backup = new File(oldDir, "v" + version + "/" + PointsConfig.FILE);
        if (backup.exists()) backup = new File(backup.getPath() + "." + System.currentTimeMillis());
        Files.createDirectories(backup.getParentFile().toPath());
        Files.writeString(backup.toPath(), original, StandardCharsets.UTF_8);
        Files.writeString(file.toPath(), upgraded, StandardCharsets.UTF_8);
        logger.info("[" + PointsConfig.FILE + "] config-version " + version + " -> " + target
                + (missing.isEmpty() ? "" : ", added sources " + missing) + "; old copy: "
                + YamlFiles.OLD_DIR + "/" + backup.getParentFile().getName() + "/" + backup.getName());
        return true;
    }

    /** Index of the last indented, non-blank line of the top-level {@code sources:} section; -1 when absent. */
    static int endOfSources(List<String> lines) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).matches("^sources:\\s*(#.*)?$")) {
                start = i;
                break;
            }
        }
        if (start < 0) return -1;
        int last = start;
        for (int i = start + 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.isBlank()) continue;
            if (!Character.isWhitespace(l.charAt(0))) break;
            last = i;
        }
        return last;
    }

    /**
     * The text of one source in the bundled file: the comment lines right above it, its id line and every deeper
     * line after it.
     */
    static List<String> sourceBlock(List<String> bundled, String id) {
        int at = -1;
        for (int i = 0; i < bundled.size(); i++) {
            if (bundled.get(i).matches("^  " + Pattern.quote(id) + ":\\s*(#.*)?$")) {
                at = i;
                break;
            }
        }
        if (at < 0) return List.of();
        int from = at;
        while (from - 1 >= 0 && bundled.get(from - 1).startsWith("  #")) from--;
        int to = at;
        for (int i = at + 1; i < bundled.size(); i++) {
            String l = bundled.get(i);
            if (l.isBlank() || !l.startsWith("    ")) break;
            to = i;
        }
        return new ArrayList<>(bundled.subList(from, to + 1));
    }

    private static int version(YamlConfiguration y) {
        return y != null && y.get("config-version") instanceof Number n ? n.intValue() : 1;
    }

    private static YamlConfiguration parse(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
            return y;
        } catch (InvalidConfigurationException ex) {
            return null;
        }
    }
}
