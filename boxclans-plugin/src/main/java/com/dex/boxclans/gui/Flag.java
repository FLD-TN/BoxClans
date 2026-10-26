package com.dex.boxclans.gui;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A clan flag (§8.3), stored as text, never as an item: {@code WHITE|RED:cross|BLUE:stripe_top} (base colour,
 * then colour:pattern layers). A banner is only built to be shown in a menu.
 *
 * @param layers bottom first
 */
public record Flag(DyeColor base, List<Layer> layers) {

    /** One pattern layer; {@code pattern} is the in-game key ({@code cross}, {@code stripe_top}). */
    public record Layer(DyeColor color, String pattern) {}

    public Flag {
        layers = List.copyOf(layers);
    }

    /** {@code null} for an empty or invalid text. */
    public static Flag parse(String text) {
        if (text == null || text.isBlank()) return null;
        String[] parts = text.trim().split("\\|");
        DyeColor base = color(parts[0]);
        if (base == null) return null;
        List<Layer> layers = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String[] cp = parts[i].split(":", 2);
            if (cp.length != 2) continue;
            DyeColor c = color(cp[0]);
            String pattern = cp[1].trim().toLowerCase(Locale.ROOT);
            if (c != null && pattern.matches("[a-z0-9_]+")) layers.add(new Layer(c, pattern));
        }
        return new Flag(base, layers);
    }

    public static DyeColor color(String name) {
        if (name == null) return null;
        try {
            return DyeColor.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String encode() {
        StringBuilder sb = new StringBuilder(base.name());
        for (Layer l : layers) sb.append('|').append(l.color().name()).append(':').append(l.pattern());
        return sb.toString();
    }

    public Flag withBase(DyeColor color) {
        return new Flag(color, layers);
    }

    public Flag with(Layer layer) {
        List<Layer> out = new ArrayList<>(layers);
        out.add(layer);
        return new Flag(base, out);
    }

    public static Material banner(DyeColor color) {
        Material m = Material.matchMaterial(color.name() + "_BANNER");
        return m == null ? Material.WHITE_BANNER : m;
    }

    public static Material dye(DyeColor color) {
        Material m = Material.matchMaterial(color.name() + "_DYE");
        return m == null ? Material.WHITE_DYE : m;
    }

    /** The banner of this flag (display only). */
    public ItemStack item() {
        ItemStack item = MenuItems.stack(banner(base), 1);
        applyPatterns(item, layers);
        return item;
    }

    /** The clan icon: its flag, or {@code fallback} when it has none. */
    public static ItemStack icon(String encoded, Material fallback) {
        Flag flag = parse(encoded);
        return flag == null ? MenuItems.stack(fallback == null ? Material.WHITE_BANNER : fallback, 1) : flag.item();
    }

    /** Adds the layers to a banner item; unknown patterns are skipped. */
    public static void applyPatterns(ItemStack item, List<Layer> layers) {
        if (layers.isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BannerMeta banner)) return;
        for (Layer l : layers) {
            PatternType type = patternType(l.pattern());
            if (type != null) banner.addPattern(new Pattern(l.color(), type));
        }
        item.setItemMeta(banner);
    }

    /** Whether the server knows a pattern key (true when the registry cannot be read, e.g. in tests). */
    public static boolean knownPattern(String key) {
        try {
            return Registry.BANNER_PATTERN.get(NamespacedKey.minecraft(key)) != null;
        } catch (RuntimeException | LinkageError ex) {
            return true;
        }
    }

    public static PatternType patternType(String key) {
        try {
            return Registry.BANNER_PATTERN.get(NamespacedKey.minecraft(key));
        } catch (RuntimeException | LinkageError ex) {
            return null;
        }
    }
}
