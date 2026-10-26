package com.dex.boxclans.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Immutable snapshot of {@code icons.yml}: the clan shield icons sold in the Shop Clan menu
 * ({@code BOXCLANS_ICON_SPEC.md} §4). Glyphs are ItemsAdder font images: {@code glyph} holds {@code {frame}}
 * (1-based) for the animated frames, {@code static} is the still icon for chat.
 */
public final class Icons {

    public static final String FILE = "icons.yml";
    public static final String FRAME = "{frame}";

    /**
     * @param defaultOwned every clan has it without buying (BOXCLANS_SHOP_SPEC.md §3); never sold in the shop
     */
    public record Icon(String id, String display, int frames, String glyph, String staticGlyph, String menuItem,
                       String fallbackMaterial, long price, String permission, boolean defaultOwned) {

        /** Sold in the clan shop: not default-owned. A price of 0 is a free claim. */
        public boolean forSale() {
            return !defaultOwned;
        }

        /** Font image id of a frame, 1-based. */
        public String frameId(int frame) {
            return glyph.replace(FRAME, String.valueOf(frame));
        }
    }

    private final String defaultIcon;
    private final long frameIntervalMs;
    private final Map<String, Icon> icons;

    private Icons(String defaultIcon, long frameIntervalMs, Map<String, Icon> icons) {
        this.defaultIcon = defaultIcon;
        this.frameIntervalMs = frameIntervalMs;
        this.icons = icons;
    }

    public static Icons from(YamlConfiguration yaml, Logger logger) {
        Map<String, Icon> icons = new LinkedHashMap<>();
        // get() falls back to the bundled section when the file has no 'icons' (getConfigurationSection would
        // create an empty one); a file that has it is authoritative, so a deleted icon stays deleted
        if (yaml.get("icons") instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null) continue;
                String id = key.toLowerCase(Locale.ROOT);
                int frames = (int) Math.max(1, YamlFiles.integer(s, "frames", 1));
                String glyph = YamlFiles.string(s, "glyph", "");
                if (frames > 1 && !glyph.contains(FRAME)) {
                    logger.warning("[" + FILE + "] icons." + key + ".glyph has no " + FRAME + ": only one frame is shown");
                    frames = 1;
                }
                icons.put(id, new Icon(id, YamlFiles.string(s, "display", id), frames, glyph,
                        YamlFiles.string(s, "static", ""), YamlFiles.string(s, "menu-item", ""),
                        YamlFiles.string(s, "fallback-material", "SHIELD"),
                        Math.max(0, YamlFiles.integer(s, "price", 0)), YamlFiles.string(s, "permission", ""),
                        YamlFiles.bool(s, "default-owned", false)));
            }
        }
        String def = YamlFiles.string(yaml, "default-icon", "none").toLowerCase(Locale.ROOT);
        if (def.equals("none")) def = "";
        if (!def.isEmpty() && !icons.containsKey(def)) {
            logger.warning("[" + FILE + "] default-icon '" + def + "' is not in icons: new clans get no icon");
            def = "";
        }
        long interval = Math.max(50, YamlFiles.integer(yaml, "frame-interval-ms", 100));
        return new Icons(def, interval, java.util.Collections.unmodifiableMap(icons));
    }

    /** Icon given to a new clan, {@code ""} = none. */
    public String defaultIcon() {
        return defaultIcon;
    }

    /** Milliseconds each animation frame stays (≥ 50). */
    public long frameIntervalMs() {
        return frameIntervalMs;
    }

    /** The icon, {@code null} when unknown. */
    public Icon icon(String id) {
        return id == null ? null : icons.get(id.toLowerCase(Locale.ROOT));
    }

    /** Every icon, in file order. */
    public Collection<Icon> all() {
        return icons.values();
    }

    public List<String> ids() {
        return new ArrayList<>(icons.keySet());
    }
}
