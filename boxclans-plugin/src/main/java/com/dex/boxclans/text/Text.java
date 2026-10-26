package com.dex.boxclans.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;

/**
 * Text helpers shared by every config file: every template may mix legacy {@code &} codes, {@code &#RRGGBB}
 * and MiniMessage tags. Templates are converted to pure MiniMessage once (at load), then filled with
 * placeholder values that are escaped, so a value can never inject a tag.
 */
public final class Text {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    /** {@code §x§R§R§G§G§B§B} hex, what scoreboards, TAB and holograms read. */
    public static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR).hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    /** {@code &c} / {@code &#RRGGBB}, for plugins that translate {@code &} codes themselves. */
    public static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();
    public static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final Map<Character, String> CODES = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"), Map.entry('m', "strikethrough"),
            Map.entry('n', "underlined"), Map.entry('o', "italic"));

    /** A placeholder value that is already MiniMessage and must be inserted as is (never user input). */
    public record Mini(String value) {
        @Override
        public String toString() {
            return value;
        }
    }

    private Text() {
    }

    /**
     * Converts legacy codes ({@code &c}, {@code &l}, {@code &r}, {@code &#RRGGBB}, {@code &x&R&R&G&G&B&B},
     * and the same with {@code §}) to MiniMessage tags; MiniMessage tags already present are kept. Like in
     * legacy text, a colour code turns off the decorations before it (with {@code <!bold>}…, never {@code <reset>},
     * so an enclosing click / hover keeps working); {@code &r} is {@code <reset>}. {@code &&} is a literal {@code &}.
     */
    public static String legacyToMini(String input) {
        if (input == null || input.isEmpty()) return "";
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) return input;
        StringBuilder out = new StringBuilder(input.length() + 16);
        // decorations opened since the last colour: a colour turns them off (like legacy text) without <reset>,
        // which would also close an enclosing <click> / <hover>
        StringBuilder active = new StringBuilder();
        int n = input.length();
        for (int i = 0; i < n; i++) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < n && input.charAt(i + 1) == '&') {
                out.append('&'); // "&&" = a literal "&"
                i++;
                continue;
            }
            if ((c == '&' || c == '§') && i + 1 < n) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                if (code == '#' && isHex(input, i + 2, 6)) {
                    closeDecorations(out, active);
                    out.append("<#").append(input, i + 2, i + 8).append('>');
                    i += 7;
                    continue;
                }
                if (code == 'x' && i + 13 < n && isRepeatedHex(input, i + 2, c)) {
                    closeDecorations(out, active);
                    out.append("<#");
                    for (int k = 0; k < 6; k++) out.append(input.charAt(i + 3 + k * 2));
                    out.append('>');
                    i += 13;
                    continue;
                }
                if (code == 'r') {
                    active.setLength(0);
                    out.append("<reset>");
                    i++;
                    continue;
                }
                String tag = CODES.get(code);
                if (tag != null) {
                    boolean color = (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
                    if (color) {
                        closeDecorations(out, active);
                    } else if (active.indexOf(String.valueOf(code)) < 0) {
                        active.append(code);
                    }
                    out.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static void closeDecorations(StringBuilder out, StringBuilder active) {
        for (int k = 0; k < active.length(); k++) out.append("<!").append(CODES.get(active.charAt(k))).append('>');
        active.setLength(0);
    }

    private static boolean isHex(String s, int from, int count) {
        if (from + count > s.length()) return false;
        for (int i = from; i < from + count; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) return false;
        }
        return true;
    }

    /** {@code &R&R&G&G&B&B} after {@code &x}. */
    private static boolean isRepeatedHex(String s, int from, char marker) {
        if (from + 12 > s.length()) return false;
        for (int k = 0; k < 6; k++) {
            if (s.charAt(from + k * 2) != marker || Character.digit(s.charAt(from + k * 2 + 1), 16) < 0) return false;
        }
        return true;
    }

    /**
     * Fills {@code %key%} placeholders. Plain values are escaped; {@link Component} and {@link Mini} values
     * are inserted as MiniMessage.
     *
     * @param placeholders alternating key, value pairs
     */
    public static String fill(String template, Object... placeholders) {
        if (template == null || template.isEmpty()) return "";
        if (placeholders.length == 0 || template.indexOf('%') < 0) return template;
        String out = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String token = "%" + placeholders[i] + "%";
            if (out.contains(token)) out = out.replace(token, value(placeholders[i + 1]));
        }
        return out;
    }

    /** The MiniMessage form of a placeholder value. */
    public static String value(Object value) {
        if (value instanceof Component component) return isolated(component);
        if (value instanceof Mini mini) return mini.value();
        return MM.escapeTags(String.valueOf(value));
    }

    /** Parses a MiniMessage string. */
    public static Component parse(String mini) {
        return MM.deserialize(mini == null ? "" : mini);
    }

    /** Parses a template (legacy or MiniMessage) without placeholders. */
    public static Component colorize(String template) {
        return parse(legacyToMini(template));
    }

    /** For item names and lore: italic explicitly off unless the text asks for it. */
    public static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    public static String legacy(Component component) {
        return LEGACY_SECTION.serialize(component);
    }

    /**
     * MiniMessage for a component that is <b>closed</b>: every colour / decoration opened for a piece of text is
     * closed right after it, so inserting it into a template never leaks its style (a bold clan name must not
     * make the rest of the message bold). Only colour, bold, italic, underlined, strikethrough and obfuscated are
     * kept (what a clan name can hold); a decoration explicitly turned off inside is written as {@code <!x>}.
     */
    public static String isolated(Component component) {
        StringBuilder out = new StringBuilder();
        isolated(component, Style.empty(), out);
        return out.toString();
    }

    private static final TextDecoration[] DECORATIONS = {TextDecoration.BOLD, TextDecoration.ITALIC,
            TextDecoration.UNDERLINED, TextDecoration.STRIKETHROUGH, TextDecoration.OBFUSCATED};

    private static void isolated(Component c, Style parent, StringBuilder out) {
        Style style = parent.merge(c.style(), Style.Merge.Strategy.ALWAYS);
        if (c instanceof TextComponent t && !t.content().isEmpty()) {
            StringBuilder close = new StringBuilder();
            if (style.color() != null) {
                out.append("<color:").append(style.color().asHexString()).append('>');
                close.insert(0, "</color>");
            }
            for (TextDecoration d : DECORATIONS) {
                TextDecoration.State state = style.decoration(d);
                if (state == TextDecoration.State.NOT_SET) continue;
                String tag = (state == TextDecoration.State.FALSE ? "!" : "") + d.name().toLowerCase(java.util.Locale.ROOT);
                out.append('<').append(tag).append('>');
                close.insert(0, "</" + tag + ">");
            }
            out.append(MM.escapeTags(t.content())).append(close);
        }
        for (Component child : c.children()) isolated(child, style, out);
    }

    /** A colour code alone as legacy text: {@code &c} → {@code §c}, {@code &#FF0000} → {@code §x§F§F§0§0§0§0}. */
    public static String legacyColor(String template) {
        if (template == null || template.isEmpty()) return "";
        String legacy = legacy(colorize(template + "x"));
        return legacy.endsWith("x") ? legacy.substring(0, legacy.length() - 1) : legacy;
    }
}
