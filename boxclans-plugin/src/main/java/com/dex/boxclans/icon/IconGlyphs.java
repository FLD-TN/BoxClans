package com.dex.boxclans.icon;

import com.dex.boxclans.config.Icons;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The characters of the shield icons ({@code BOXCLANS_ICON_SPEC.md} §5). Built on the main thread from the
 * ItemsAdder font images (again after an ItemsAdder reload or {@code /clanadmin reload}); read from any thread by
 * the placeholders. The animated frame comes from the clock, so every viewer sees the same frame.
 *
 * <p>Values start with {@code §f}: a font image takes the colour of the text before it, white keeps its own
 * colours. They end with one space so {@code [%boxclans_icon%%boxclans_name%]} reads {@code [🛡 Tên]}, and are
 * empty (no space) without an icon.
 */
public final class IconGlyphs {

    /** ItemsAdder font image id → its character, {@code ""} when it does not exist. */
    @FunctionalInterface
    public interface Resolver {
        String glyph(String fontImageId);
    }

    private record Entry(String[] frames, String still) {}

    private static final String WHITE = "§f";

    private final LongSupplier clock;
    private volatile Map<String, Entry> entries = Map.of();
    private volatile long intervalMs = 100;

    public IconGlyphs(LongSupplier clock) {
        this.clock = clock;
    }

    /** Main thread: reads every glyph of {@code icons}; a missing resolver (no ItemsAdder) leaves them empty. */
    public void rebuild(Icons icons, Resolver resolver) {
        Map<String, Entry> out = new HashMap<>();
        for (Icons.Icon icon : icons.all()) {
            String[] frames = new String[icon.frames()];
            for (int i = 0; i < frames.length; i++) frames[i] = resolve(resolver, icon.frameId(i + 1));
            out.put(icon.id(), new Entry(frames, resolve(resolver, icon.staticGlyph())));
        }
        intervalMs = icons.frameIntervalMs();
        entries = Map.copyOf(out);
    }

    private static String resolve(Resolver resolver, String id) {
        if (resolver == null || id == null || id.isEmpty()) return "";
        String g = resolver.glyph(id);
        return g == null ? "" : g;
    }

    /** Current animation frame + space, {@code ""} for no icon or a glyph that does not exist. */
    public String animated(String icon) {
        Entry e = icon == null || icon.isEmpty() ? null : entries.get(icon);
        if (e == null || e.frames().length == 0) return "";
        int frame = (int) Math.floorMod(clock.getAsLong() / intervalMs, (long) e.frames().length);
        return decorate(e.frames()[frame]);
    }

    /** Still icon + space (chat), {@code ""} when none. Falls back to the first frame without a static glyph. */
    public String still(String icon) {
        String raw = stillRaw(icon);
        return decorate(raw);
    }

    /** The still character alone (no colour, no space), {@code ""} when none; for menus. */
    public String stillRaw(String icon) {
        Entry e = icon == null || icon.isEmpty() ? null : entries.get(icon);
        if (e == null) return "";
        if (!e.still().isEmpty()) return e.still();
        return e.frames().length == 0 ? "" : e.frames()[0];
    }

    private static String decorate(String glyph) {
        return glyph == null || glyph.isEmpty() ? "" : WHITE + glyph + " ";
    }
}
