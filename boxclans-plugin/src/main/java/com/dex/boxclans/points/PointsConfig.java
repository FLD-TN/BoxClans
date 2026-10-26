package com.dex.boxclans.points;

import com.dex.boxclans.config.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Immutable snapshot of {@code points.yml} (§5.1, §5.4). Sources keep the file order (the score lore uses it). */
public record PointsConfig(boolean allowNegative, String boostType, ZoneId zone, int logKeepDays,
                           Map<String, PointSource> sources, Map<String, KillEventProfile> killEvents) {

    public static final String FILE = "points.yml";

    /** A kill event profile (§5.4). {@code display} is a template ({@code &}, hex, MiniMessage). */
    public record KillEventProfile(String id, String display, long kill, long death, boolean ignorePairLimit,
                                   boolean ignoreClanDailyCap) {}

    public static PointsConfig from(YamlConfiguration y, Logger logger) {
        ZoneId zone;
        String tz = YamlFiles.string(y, "day-reset-timezone", "Asia/Ho_Chi_Minh");
        try {
            zone = ZoneId.of(tz);
        } catch (DateTimeException ex) {
            logger.warning("[" + FILE + "] Unknown day-reset-timezone '" + tz + "', using Asia/Ho_Chi_Minh");
            zone = ZoneId.of("Asia/Ho_Chi_Minh");
        }
        Map<String, PointSource> sources = new LinkedHashMap<>();
        ConfigurationSection s = y.getConfigurationSection("sources");
        if (s != null) {
            for (String id : keys(s)) {
                ConfigurationSection src = s.getConfigurationSection(id);
                if (src == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                sources.put(key, new PointSource(key,
                        YamlFiles.integer(src, "points", 0),
                        YamlFiles.bool(src, "enabled", true),
                        YamlFiles.string(src, "display", id),
                        (int) Math.max(1, YamlFiles.integer(src, "every", 1)),
                        Math.max(0, YamlFiles.integer(src, "cooldown", 0)),
                        Math.max(0, YamlFiles.integer(src, "daily-cap", 0)),
                        Math.max(0, YamlFiles.integer(src, "clan-daily-cap", 0)),
                        YamlFiles.bool(src, "boost", false),
                        YamlFiles.bool(src, "contribution", true),
                        YamlFiles.bool(src, "announce", true),
                        src.contains("lore") ? YamlFiles.string(src, "lore", "") : null));
            }
        }
        Map<String, KillEventProfile> events = new LinkedHashMap<>();
        ConfigurationSection k = y.getConfigurationSection("kill-events");
        if (k != null) {
            for (String id : keys(k)) {
                ConfigurationSection e = k.getConfigurationSection(id);
                if (e == null) continue;
                boolean all = YamlFiles.bool(e, "ignore-limits", false);
                String key = id.toLowerCase(Locale.ROOT);
                events.put(key, new KillEventProfile(key, YamlFiles.string(e, "display", id),
                        YamlFiles.integer(e, "kill", 10), YamlFiles.integer(e, "death", 0),
                        YamlFiles.bool(e, "ignore-pair-limit", all), YamlFiles.bool(e, "ignore-clan-daily-cap", all)));
            }
        }
        return new PointsConfig(YamlFiles.bool(y, "allow-negative", false),
                YamlFiles.string(y, "boxcore-boost-type", ""), zone,
                (int) Math.max(0, YamlFiles.integer(y, "log-keep-days", 30)),
                Collections.unmodifiableMap(sources), Collections.unmodifiableMap(events));
    }

    /**
     * Ids of a section: the file's own ids when it declares any (removing a source removes it), otherwise the
     * bundled ones. Missing keys inside an id still fall back to the bundled values.
     */
    private static Set<String> keys(ConfigurationSection s) {
        Set<String> own = s.getKeys(false);
        if (!own.isEmpty()) return own;
        ConfigurationSection def = s.getDefaultSection();
        return def == null ? Set.of() : def.getKeys(false);
    }

    public PointSource source(String id) {
        return id == null ? null : sources.get(id.toLowerCase(Locale.ROOT));
    }

    public KillEventProfile killEvent(String id) {
        return id == null ? null : killEvents.get(id.toLowerCase(Locale.ROOT));
    }
}
