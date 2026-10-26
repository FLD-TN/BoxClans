package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.Outcome;
import com.dex.boxclans.clan.RolePermission;
import com.dex.boxclans.config.YamlFiles;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@code menus/flag-maker.yml} (§8.3): base colour, pattern colour and pattern layers, with preview, undo, reset
 * and save. The draft lives in this menu; nothing is ever given to the player, and the flag is saved as text.
 */
public final class FlagMakerMenu extends Menu {

    enum Mode { BASE, COLORS, PATTERNS }

    private static final int CONTENT = 45;

    private final ConfigurationSection cfg;
    /** Previous drafts for undo; empty = no flag. */
    private final Deque<Optional<Flag>> history = new ArrayDeque<>();
    private Flag draft;
    private DyeColor patternColor = DyeColor.BLACK;
    private Mode mode;

    public FlagMakerMenu(BoxClansPlugin plugin, Player viewer) {
        super(plugin, viewer);
        this.cfg = plugin.menus().menu("flag-maker");
        ClanData clan = plugin.manager().of(viewer.getUniqueId());
        this.draft = clan == null ? null : Flag.parse(clan.flag());
        this.mode = draft == null ? Mode.BASE : Mode.PATTERNS;
    }

    private String text(String path) {
        return YamlFiles.string(cfg, path, "");
    }

    @Override
    protected Component title() {
        return MenuItems.line(text("title"));
    }

    @Override
    protected int rows() {
        return 6;
    }

    private int maxLayers() {
        int n = plugin.perks().bannerPatterns(viewer::hasPermission);
        return n < 0 ? Integer.MAX_VALUE : n;
    }

    private String colorName(DyeColor c) {
        String label = text("color-labels." + c.name().toLowerCase(Locale.ROOT));
        return label.isEmpty() ? c.name().toLowerCase(Locale.ROOT) : label;
    }

    private String patternName(String key) {
        String label = text("pattern-labels." + key);
        return label.isEmpty() ? key : label;
    }

    /** Patterns offered: the keys of {@code pattern-labels}, in file order. */
    private List<String> patterns() {
        ConfigurationSection labels = cfg.getConfigurationSection("pattern-labels");
        if (labels == null) return List.of();
        List<String> out = new ArrayList<>(labels.getKeys(false));
        if (out.isEmpty() && labels.getDefaultSection() != null) out.addAll(labels.getDefaultSection().getKeys(false));
        return out;
    }

    private Object[] ph() {
        int layers = draft == null ? 0 : draft.layers().size();
        int max = maxLayers();
        return new Object[]{"layers", layers, "max_layers", max == Integer.MAX_VALUE ? "∞" : String.valueOf(max),
                "color_name", colorName(patternColor)};
    }

    @Override
    protected void draw() {
        ItemStack filler = MenuItems.build(cfg.getConfigurationSection("filler"));
        for (int i = 0; i < 54; i++) set(i, filler, null);
        switch (mode) {
            case BASE -> drawBase();
            case COLORS -> drawColors();
            case PATTERNS -> drawPatterns();
        }
        drawControls();
    }

    private void drawBase() {
        ConfigurationSection s = cfg.getConfigurationSection("base-color-item");
        int slot = 0;
        for (DyeColor c : DyeColor.values()) {
            if (slot >= CONTENT) break;
            ItemStack item = MenuItems.build(Flag.banner(c), s, null, "color_name", colorName(c));
            set(slot++, item, click -> {
                push();
                draft = draft == null ? new Flag(c, List.of()) : draft.withBase(c);
                mode = Mode.PATTERNS;
                plugin.sounds().play(viewer, "flag-pattern-applied");
                refresh();
            });
        }
    }

    private void drawColors() {
        ConfigurationSection s = cfg.getConfigurationSection("color-picker-item");
        int slot = 0;
        for (DyeColor c : DyeColor.values()) {
            if (slot >= CONTENT) break;
            boolean selected = c == patternColor;
            ItemStack item = MenuItems.build(Flag.dye(c), s, null, "color_name", colorName(c));
            ItemMeta meta = item.getItemMeta();
            if (meta != null && s != null && selected) {
                meta.displayName(MenuItems.line(YamlFiles.string(s, "name-selected", "%color_name%"), "color_name", colorName(c)));
                meta.lore(MenuItems.lore(s.getStringList("lore-selected"), "color_name", colorName(c)));
                meta.setEnchantmentGlintOverride(true);
                item.setItemMeta(meta);
            }
            set(slot++, item, click -> {
                patternColor = c;
                mode = Mode.PATTERNS;
                refresh();
            });
        }
    }

    private void drawPatterns() {
        if (draft == null) {
            mode = Mode.BASE;
            drawBase();
            return;
        }
        ConfigurationSection s = cfg.getConfigurationSection("pattern-item");
        int slot = 0;
        for (String key : patterns()) {
            if (slot >= CONTENT) break;
            if (!Flag.knownPattern(key)) continue;
            List<Flag.Layer> preview = new ArrayList<>(draft.layers());
            preview.add(new Flag.Layer(patternColor, key));
            ItemStack item = MenuItems.stack(Flag.banner(draft.base()), 1);
            Flag.applyPatterns(item, preview);
            Object[] ph = PagedMenu.join(ph(), new Object[]{"pattern_name", patternName(key)});
            MenuItems.decorate(item, s, null, ph);
            set(slot++, item, click -> addLayer(key));
        }
    }

    private void addLayer(String key) {
        if (draft == null) return;
        if (draft.layers().size() >= maxLayers()) {
            plugin.messages().send(viewer, "flag-max-layers", "max", maxLayers());
            plugin.sounds().play(viewer, "error");
            return;
        }
        push();
        draft = draft.with(new Flag.Layer(patternColor, key));
        plugin.sounds().play(viewer, "flag-pattern-applied");
        refresh();
    }

    private void push() {
        history.push(Optional.ofNullable(draft));
        while (history.size() > 64) history.removeLast();
    }

    private void control(String name, Consumer<ClickType> action, ItemStack override) {
        ConfigurationSection s = cfg.getConfigurationSection("controls." + name);
        if (s == null) return;
        int slot = (int) YamlFiles.integer(s, "slot", -1);
        if (slot < 0 || slot >= 54) return;
        ItemStack item = override != null ? MenuItems.decorate(override, s, null, ph()) : MenuItems.build(s, ph());
        set(slot, item, action);
    }

    private void drawControls() {
        ItemStack preview = draft == null ? null : draft.item();
        control("flag-preview", click -> {
            mode = Mode.BASE;
            refresh();
        }, preview);
        control("change-pattern-color", click -> {
            if (draft == null) return;
            mode = Mode.COLORS;
            refresh();
        }, null);
        control("back", click -> plugin.guis().openSettings(viewer), null);
        control("undo", click -> {
            if (history.isEmpty()) return;
            draft = history.pop().orElse(null);
            mode = draft == null ? Mode.BASE : Mode.PATTERNS;
            refresh();
        }, null);
        control("reset", click -> {
            if (draft != null) push();
            draft = null;
            mode = Mode.BASE;
            plugin.sounds().play(viewer, "flag-reset");
            refresh();
        }, null);
        control("save", click -> save(), null);
    }

    private void save() {
        ClanData clan = plugin.manager().of(viewer.getUniqueId());
        if (clan == null) {
            viewer.closeInventory();
            return;
        }
        if (!plugin.manager().can(clan, viewer.getUniqueId(), RolePermission.EDIT_FLAG)) {
            plugin.actions().fail(viewer, Outcome.NO_PERMISSION);
            return;
        }
        Outcome o = plugin.manager().setFlag(viewer.getUniqueId(), draft == null ? "" : draft.encode());
        if (o != Outcome.OK) {
            plugin.actions().fail(viewer, o);
            return;
        }
        plugin.messages().send(viewer, draft == null ? "flag-cleared" : "flag-saved");
        plugin.sounds().play(viewer, "flag-saved");
        plugin.guis().openSettings(viewer);
    }

    /** Test access to the draft. */
    Flag draft() {
        return draft;
    }
}
