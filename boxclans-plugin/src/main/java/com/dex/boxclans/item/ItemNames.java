package com.dex.boxclans.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/** The name of an item for messages (with its colours) and for the history (plain). */
public final class ItemNames {

    private ItemNames() {
    }

    /** MiniMessage: the custom name, else the vanilla name in the client's language. */
    public static String mini(ItemStack item) {
        Component name = custom(item);
        if (name != null) return MiniMessage.miniMessage().serialize(name);
        Material m = item == null ? Material.AIR : item.getType();
        try {
            return "<lang:" + m.translationKey() + ">";
        } catch (RuntimeException | LinkageError noServer) {
            return pretty(m);
        }
    }

    /** Plain text: the custom name, else the material written as words ("Honey Bottle"). */
    public static String plain(ItemStack item) {
        Component name = custom(item);
        if (name != null) return PlainTextComponentSerializer.plainText().serialize(name);
        return pretty(item == null ? Material.AIR : item.getType());
    }

    private static Component custom(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.displayName() : null;
    }

    static String pretty(Material m) {
        String[] words = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }
}
