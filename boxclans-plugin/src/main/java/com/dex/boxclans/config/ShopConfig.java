package com.dex.boxclans.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Immutable snapshot of {@code shop.yml} (BOXCLANS_SHOP_SPEC.md §4.1): the categories of the clan shop and storage,
 * and when the weekly purchase limits reset. The owner edits it; the plugin never writes it.
 */
public final class ShopConfig {

    public static final String FILE = "shop.yml";
    /** Id of the tab showing every category. Not a category. */
    public static final String ALL = "all";
    /** Category that receives entries of a category deleted from the file. */
    public static final String OTHER = "khac";

    /**
     * @param icons true for the category listing the shield icons of {@code icons.yml} (no stored entries)
     */
    public record Category(String id, String name, String material, boolean icons) {}

    private final List<Category> categories;
    private final DayOfWeek resetDay;
    private final LocalTime resetTime;
    private final ZoneId zone;

    private ShopConfig(List<Category> categories, DayOfWeek resetDay, LocalTime resetTime, ZoneId zone) {
        this.categories = categories;
        this.resetDay = resetDay;
        this.resetTime = resetTime;
        this.zone = zone;
    }

    public static ShopConfig from(YamlConfiguration yaml, Logger logger) {
        List<Category> list = new ArrayList<>();
        // get() falls back to the bundled section only when the file has none: a file section is authoritative
        if (yaml.get("categories") instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                String id = key.toLowerCase(Locale.ROOT);
                if (s == null || id.equals(ALL)) {
                    logger.warning("[" + FILE + "] categories." + key + " is not a valid category, skipped");
                    continue;
                }
                list.add(new Category(id, YamlFiles.string(s, "name", key), YamlFiles.string(s, "material", "CHEST"),
                        YamlFiles.bool(s, "icons", false)));
            }
        }
        if (list.stream().noneMatch(c -> !c.icons())) {
            logger.warning("[" + FILE + "] no item category, adding '" + OTHER + "'");
            list.add(new Category(OTHER, "&#AAAAAAKhác", "CHEST", false));
        }
        DayOfWeek day = DayOfWeek.MONDAY;
        String rawDay = YamlFiles.string(yaml, "weekly-reset.day", "MONDAY");
        try {
            day = DayOfWeek.valueOf(rawDay.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            logger.warning("[" + FILE + "] weekly-reset.day '" + rawDay + "' is not a day, using MONDAY");
        }
        LocalTime time = LocalTime.MIDNIGHT;
        String rawTime = YamlFiles.string(yaml, "weekly-reset.time", "00:00");
        try {
            time = LocalTime.parse(rawTime.trim());
        } catch (DateTimeException ex) {
            logger.warning("[" + FILE + "] weekly-reset.time '" + rawTime + "' is not HH:mm, using 00:00");
        }
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        String rawZone = YamlFiles.string(yaml, "weekly-reset.timezone", "Asia/Ho_Chi_Minh");
        try {
            zone = ZoneId.of(rawZone.trim());
        } catch (DateTimeException ex) {
            logger.warning("[" + FILE + "] weekly-reset.timezone '" + rawZone + "' is unknown, using Asia/Ho_Chi_Minh");
        }
        return new ShopConfig(Collections.unmodifiableList(list), day, time, zone);
    }

    /** Categories in file order (the tab order). */
    public List<Category> categories() {
        return categories;
    }

    /** Categories that hold items (not the icon one): the storage tabs. */
    public List<Category> itemCategories() {
        return categories.stream().filter(c -> !c.icons()).toList();
    }

    public Category category(String id) {
        if (id == null) return null;
        for (Category c : categories) if (c.id().equalsIgnoreCase(id)) return c;
        return null;
    }

    /** The icon category, {@code null} when the file has none (icons are then only in "Tất cả"). */
    public Category iconCategory() {
        for (Category c : categories) if (c.icons()) return c;
        return null;
    }

    /**
     * The category an item lives in: its own when it still exists and holds items, otherwise {@link #OTHER} (or the
     * first item category when there is no {@code khac}).
     */
    public String itemCategory(String id) {
        Category c = category(id);
        if (c != null && !c.icons()) return c.id();
        Category other = category(OTHER);
        return other != null && !other.icons() ? other.id() : itemCategories().get(0).id();
    }

    /** Start of the purchase week containing {@code now} (epoch ms): the last reset day and time at or before it. */
    public long weekStart(long now) {
        ZonedDateTime at = java.time.Instant.ofEpochMilli(now).atZone(zone);
        ZonedDateTime start = at.with(TemporalAdjusters.previousOrSame(resetDay)).with(resetTime);
        if (start.isAfter(at)) start = start.minusWeeks(1);
        return start.toInstant().toEpochMilli();
    }
}
