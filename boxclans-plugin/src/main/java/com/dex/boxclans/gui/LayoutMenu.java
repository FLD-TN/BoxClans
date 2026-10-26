package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.config.YamlFiles;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A menu laid out by a {@code menus/<name>.yml} file (§8.4): {@code title}, {@code rows}, {@code layout} (one
 * string of 9 characters per row), {@code items.<char>} for the buttons and {@code filler} for the {@code .}
 * slots. Subclasses place the dynamic content and bind the actions.
 */
public abstract class LayoutMenu extends Menu {

    protected final YamlConfiguration cfg;

    protected LayoutMenu(BoxClansPlugin plugin, Player viewer, String name) {
        super(plugin, viewer);
        this.cfg = plugin.menus().menu(name);
    }

    /** Placeholders of the title. */
    protected Object[] titlePlaceholders() {
        return new Object[0];
    }

    @Override
    protected Component title() {
        return MenuItems.line(YamlFiles.string(cfg, "title", ""), titlePlaceholders());
    }

    @Override
    protected int rows() {
        List<String> layout = layout();
        return layout.isEmpty() ? (int) YamlFiles.integer(cfg, "rows", 3) : layout.size();
    }

    protected List<String> layout() {
        return cfg.getStringList("layout");
    }

    /** Slots holding a layout character, in reading order. */
    protected List<Integer> slots(char c) {
        List<Integer> out = new ArrayList<>();
        List<String> layout = layout();
        for (int row = 0; row < layout.size() && row < 6; row++) {
            String line = layout.get(row);
            for (int col = 0; col < 9 && col < line.length(); col++) {
                if (line.charAt(col) == c) out.add(row * 9 + col);
            }
        }
        return out;
    }

    protected ConfigurationSection section(String path) {
        return cfg.getConfigurationSection(path);
    }

    protected ConfigurationSection itemSection(char c) {
        return cfg.getConfigurationSection("items." + c);
    }

    /** Puts one item on every slot of a character. */
    protected void place(char c, ItemStack item, Consumer<ClickType> action) {
        for (int slot : slots(c)) set(slot, item, action);
    }

    /** Puts {@code items.<c>} on every slot of the character. */
    protected void placeStatic(char c, Consumer<ClickType> action, Object... placeholders) {
        ItemStack item = MenuItems.build(itemSection(c), placeholders);
        if (item != null) place(c, item, action);
    }

    /** Fills the {@code .} slots with {@code filler}. */
    protected void fillEmpty() {
        ItemStack filler = MenuItems.build(section("filler"));
        if (filler != null) place('.', filler, null);
    }

    /**
     * The {@code U} slots, one under each tab (1.4.3): {@code tab-marker-selected} (lime) under the open tab,
     * {@code tab-marker} (gray) under the others. Clicking a marker under a tab opens that tab.
     */
    protected void drawTabMarkers(int tabs, int selected, java.util.function.IntConsumer open) {
        ItemStack off = MenuItems.build(section("tab-marker"));
        if (off == null) off = MenuItems.build(section("filler"));
        ItemStack on = MenuItems.build(section("tab-marker-selected"));
        if (on == null) on = off;
        List<Integer> slots = slots('U');
        for (int i = 0; i < slots.size(); i++) {
            int index = i;
            ItemStack item = i == selected ? on : off;
            if (item != null) set(slots.get(i), item, i < tabs ? click -> open.accept(index) : null);
        }
    }

    /** A text from the menu file ({@code ""} if absent). */
    protected String text(String path) {
        return YamlFiles.string(cfg, path, "");
    }
}
