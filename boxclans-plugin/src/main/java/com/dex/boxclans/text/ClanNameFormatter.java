package com.dex.boxclans.text;

import com.dex.boxclans.config.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the text a player typed into a {@link ClanName} (§4): {@code &} codes, hex as {@code &#RRGGBB},
 * {@code &x&R&R&G&G&B&B} or {@code <#RRGGBB>}, the MiniMessage colour tags and bold / italic / underlined. Every other
 * tag (gradient, rainbow, click, hover, font, obfuscated…) is refused by {@link #check} and never interpreted by
 * {@link #render}.
 */
public final class ClanNameFormatter {

    /** Only the tags a clan name may use. */
    static final MiniMessage SAFE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(TextDecoration.BOLD),
                    StandardTags.decorations(TextDecoration.ITALIC),
                    StandardTags.decorations(TextDecoration.UNDERLINED),
                    StandardTags.reset()))
            .build();

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_KEY = Pattern.compile("[^\\p{L}\\p{N}]+");
    /** Letters (Vietnamese included), digits, single spaces between words. */
    private static final Pattern PLAIN = Pattern.compile("[\\p{L}\\p{N}]+( [\\p{L}\\p{N}]+)*");
    private static final Pattern LEGACY = Pattern.compile("[&§]([0-9a-fA-Fk-oK-OrRxX#])");
    private static final Pattern HEX_LEGACY = Pattern.compile("&#[0-9a-fA-F]{6}");
    /** {@code &x&R&R&G&G&B&B}. */
    private static final Pattern HEX_X = Pattern.compile("&[xX](&[0-9a-fA-F]){6}");
    private static final Pattern TAG = Pattern.compile("<(/?)([^<>/\\s]+)[^<>]*>");

    /** The kinds of formatting a permission can be attached to (§4.1, {@code clan-name.color-permissions}). */
    public enum Style { COLORS, FORMATS, HEX }

    /** Why a name was refused. */
    public enum Problem {
        /** Shorter than {@code min-length} once colours are removed. */
        TOO_SHORT,
        TOO_LONG,
        /** Characters other than letters, digits and single spaces. */
        BAD_CHARACTERS,
        /** {@code &k}, {@code &m}, click / hover / font or any other unsupported tag. */
        FORBIDDEN_FORMAT,
        /** The player lacks the permission of a formatting kind ({@link Check#style()}). */
        STYLE_PERMISSION,
        /** The command key contains a banned word ({@link Check#detail()}). */
        BANNED_WORD
    }

    /**
     * Result of {@link #check}: {@code problem == null} means the name is valid.
     *
     * @param style  the refused formatting kind for {@link Problem#STYLE_PERMISSION}
     * @param detail the banned word, or the refused code / tag
     */
    public record Check(ClanName name, Problem problem, Style style, String detail) {
        public boolean ok() {
            return problem == null;
        }
    }

    private ClanNameFormatter() {
    }

    /**
     * Colour of the text before the first colour code ({@code &6}): a name typed without colours is gold, and only
     * the uncoloured start of a partly coloured name ({@code Rồng &cĐỏ}) takes it. Applied when rendering, so it
     * also covers clans created before it existed; the stored name and the key do not change.
     */
    public static final NamedTextColor DEFAULT_COLOR = NamedTextColor.GOLD;

    /** Builds every form of a name. Never throws; validation is {@link #check}. */
    public static ClanName render(String raw) {
        String input = raw == null ? "" : raw.trim();
        String mini = Text.legacyToMini(input);
        Component component = SAFE.deserialize(mini).colorIfAbsent(DEFAULT_COLOR);
        String plain = Text.plain(component);
        return new ClanName(input, key(plain), plain, component, Text.legacy(component),
                Text.LEGACY_AMPERSAND.serialize(component), Text.isolated(component));
    }

    /** Command key: accents removed ({@code đ} → {@code d}), then everything but letters and digits removed. */
    public static String key(String plain) {
        String s = plain.replace('đ', 'd').replace('Đ', 'D');
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = MARKS.matcher(s).replaceAll("");
        return NOT_KEY.matcher(s).replaceAll("");
    }

    /**
     * Checks a typed name against the rules of §4.1. Uniqueness is checked by the caller (it needs the clans).
     *
     * @param has permission check of the player (for {@code color-permissions})
     */
    public static Check check(String raw, Settings settings, Predicate<String> has) {
        String input = raw == null ? "" : raw.trim();
        Set<Style> used = EnumSet.noneOf(Style.class);

        Matcher legacy = LEGACY.matcher(input);
        int skipUntil = -1;
        while (legacy.find()) {
            if (legacy.start() < skipUntil) continue; // the digits of an &x&R&R&G&G&B&B colour
            char code = Character.toLowerCase(legacy.group(1).charAt(0));
            if (input.charAt(legacy.start()) == '§') {
                return refuse(input, Problem.FORBIDDEN_FORMAT, null, legacy.group());
            }
            if (code == '#') {
                if (!HEX_LEGACY.matcher(input).region(legacy.start(), input.length()).lookingAt()) continue;
                used.add(Style.HEX);
            } else if (code == 'x') {
                Matcher x = HEX_X.matcher(input).region(legacy.start(), input.length());
                if (!x.lookingAt()) return refuse(input, Problem.FORBIDDEN_FORMAT, null, legacy.group());
                used.add(Style.HEX);
                skipUntil = x.end();
            } else if (code == 'k' || code == 'm') {
                return refuse(input, Problem.FORBIDDEN_FORMAT, null, legacy.group());
            } else if (code == 'l' || code == 'n' || code == 'o') {
                used.add(Style.FORMATS);
            } else if (code != 'r') {
                used.add(Style.COLORS);
            }
        }

        Matcher tags = TAG.matcher(input);
        while (tags.find()) {
            String name = tags.group(2).toLowerCase(Locale.ROOT);
            int colon = name.indexOf(':');
            String head = colon >= 0 ? name.substring(0, colon) : name;
            if (!tags.group(1).isEmpty()) {
                // closing tag: allowed when it closes an allowed tag, counts as no style
                if (head.equals("color") || head.equals("colour") || head.equals("c") || tagStyle(head, name) != null) continue;
                return refuse(input, Problem.FORBIDDEN_FORMAT, null, tags.group());
            }
            Style style = tagStyle(head, name);
            if (style == null) {
                if (head.equals("reset") || head.equals("r")) continue;
                return refuse(input, Problem.FORBIDDEN_FORMAT, null, tags.group());
            }
            used.add(style);
        }

        for (Style style : used) {
            String perm = permission(settings, style);
            if (perm != null && !perm.isEmpty() && !has.test(perm)) {
                return refuse(input, Problem.STYLE_PERMISSION, style, perm);
            }
        }

        ClanName name = render(input);
        String plain = name.plain();
        int length = plain.codePointCount(0, plain.length());
        if (length < settings.nameMinLength()) return new Check(name, Problem.TOO_SHORT, null, "");
        if (length > settings.nameMaxLength()) return new Check(name, Problem.TOO_LONG, null, "");
        if (!PLAIN.matcher(plain).matches()) return new Check(name, Problem.BAD_CHARACTERS, null, "");
        String lowerKey = name.key().toLowerCase(Locale.ROOT);
        for (String word : settings.bannedWords()) {
            String w = key(word).toLowerCase(Locale.ROOT);
            if (!w.isEmpty() && lowerKey.contains(w)) return new Check(name, Problem.BANNED_WORD, null, word);
        }
        return new Check(name, null, null, "");
    }

    private static Check refuse(String input, Problem problem, Style style, String detail) {
        return new Check(render(input), problem, style, detail);
    }

    /** The formatting kind of an opening or closing tag, {@code null} when the tag is not allowed. */
    private static Style tagStyle(String head, String full) {
        switch (head) {
            case "bold", "b", "italic", "i", "em", "underlined", "u" -> {
                return Style.FORMATS;
            }
            case "color", "colour", "c" -> {
                String value = full.substring(full.indexOf(':') + 1);
                return value.startsWith("#") ? Style.HEX : NamedTextColor.NAMES.value(value) != null ? Style.COLORS : null;
            }
            default -> {
                if (head.startsWith("#") && head.length() == 7) return Style.HEX;
                return NamedTextColor.NAMES.value(head) != null ? Style.COLORS : null;
            }
        }
    }

    private static String permission(Settings s, Style style) {
        return switch (style) {
            case COLORS -> s.permColors();
            case FORMATS -> s.permFormats();
            case HEX -> s.permHex();
        };
    }
}
