package com.dex.boxclans.gui;

import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds display items from {@code menus/*.yml} sections: {@code material}, {@code name}, {@code lore},
 * {@code amount}, {@code enchantment-glint}. Text accepts {@code &}, {@code &#hex} and MiniMessage; placeholders
 * are {@code %key%}. A lore line that is exactly {@code %key%} whose value is a {@link List} expands into one line
 * per element: a {@link Text.Mini} element is inserted as is, any other element is a template.
 */
public final class MenuItems {

    /** Creates the bare stacks; replaced by tests (a stack needs the server's registries). */
    public interface Factory {
        ItemStack create(Material material, int amount);
    }

    private static volatile Factory factory = ItemStack::new;

    private MenuItems() {
    }

    /** Test hook. */
    public static void factory(Factory f) {
        factory = f;
    }

    public static ItemStack stack(Material material, int amount) {
        return factory.create(material, amount);
    }

    public static Material material(String name, Material fallback) {
        if (name == null || name.isEmpty()) return fallback;
        Material m = Material.matchMaterial(name.trim());
        return m == null ? fallback : m;
    }

    public static boolean isAir(Material m) {
        return m == null || m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR;
    }

    /** Item from a section; {@code null} section gives {@code null}. */
    public static ItemStack build(ConfigurationSection s, Object... placeholders) {
        if (s == null) return null;
        Material material = material(s.getString("material"), Material.STONE);
        return build(material, s, null, placeholders);
    }

    /** Item from a section with another material (flags, heads) and an optional head owner. */
    public static ItemStack build(Material material, ConfigurationSection s, OfflinePlayer head, Object... placeholders) {
        if (isAir(material)) return stack(Material.AIR, 1);
        ItemStack item = stack(material, Math.max(1, Math.min(64, s == null ? 1 : (int) YamlFiles.integer(s, "amount", 1))));
        decorate(item, s, head, placeholders);
        return item;
    }

    /** Applies name, lore and glint of a section to an existing item (e.g. a banner built from a flag). */
    public static ItemStack decorate(ItemStack item, ConfigurationSection s, OfflinePlayer head, Object... placeholders) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (s != null) {
            String name = s.getString("name");
            if (name != null) meta.displayName(line(name, placeholders));
            meta.lore(lore(s.getStringList("lore"), placeholders));
            if (YamlFiles.bool(s, "enchantment-glint", false) || YamlFiles.bool(s, "glow", false)) {
                meta.setEnchantmentGlintOverride(true);
            }
        }
        meta.addItemFlags(ItemFlag.values());
        if (head != null && meta instanceof SkullMeta skull) skull.setOwningPlayer(head);
        item.setItemMeta(meta);
        return item;
    }

    /** One line of text, italic off. */
    public static Component line(String template, Object... placeholders) {
        return Text.noItalic(Text.parse(Text.fill(Text.legacyToMini(template), placeholders)));
    }

    /** Lore lines, with list placeholders expanded. */
    public static List<Component> lore(List<String> templates, Object... placeholders) {
        Map<String, List<?>> lists = new HashMap<>();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            if (placeholders[i + 1] instanceof List<?> list) lists.put("%" + placeholders[i] + "%", list);
        }
        List<Component> out = new ArrayList<>();
        for (String t : templates) {
            List<?> expand = lists.get(t.trim());
            if (expand != null) {
                for (Object o : expand) {
                    // a Mini element is already filled MiniMessage: never re-read its & codes (player data)
                    out.add(o instanceof Text.Mini m ? Text.noItalic(Text.parse(m.value())) : line(String.valueOf(o), placeholders));
                }
            } else {
                out.add(line(t, placeholders));
            }
        }
        return out;
    }
}
