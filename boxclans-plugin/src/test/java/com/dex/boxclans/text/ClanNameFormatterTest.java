package com.dex.boxclans.text;

import com.dex.boxclans.config.Settings;
import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.text.ClanNameFormatter.Check;
import com.dex.boxclans.text.ClanNameFormatter.Problem;
import com.dex.boxclans.text.ClanNameFormatter.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanNameFormatterTest {

    private static final Predicate<String> ALL = p -> true;

    private static Settings settings() {
        return settings(null);
    }

    private static Settings settings(String hexPermission) {
        YamlConfiguration y = YamlFiles.load(new File("does-not-exist.yml"),
                ClanNameFormatterTest.class.getClassLoader().getResourceAsStream("config.yml"), Logger.getLogger("t"));
        if (hexPermission != null) y.set("clan-name.color-permissions.hex", hexPermission);
        return Settings.from(y);
    }

    private static Check check(String raw) {
        return ClanNameFormatter.check(raw, settings(), ALL);
    }

    @Test
    void specExamples() {
        Check a = check("&c&lRồng &6Đỏ");
        assertTrue(a.ok());
        assertEquals("RongDo", a.name().key());
        assertEquals("Rồng Đỏ", a.name().plain());
        java.util.Map<String, net.kyori.adventure.text.format.Style> styles = new java.util.LinkedHashMap<>();
        leaves(a.name().component(), net.kyori.adventure.text.format.Style.empty(), styles);
        assertEquals(0xFF5555, styles.get("Rồng ").color().value());
        assertEquals(TextDecoration.State.TRUE, styles.get("Rồng ").decoration(TextDecoration.BOLD));
        assertEquals(0xFFAA00, styles.get("Đỏ").color().value());
        assertTrue(styles.get("Đỏ").decoration(TextDecoration.BOLD) != TextDecoration.State.TRUE, "&6 ends the bold");

        Check b = check("&x&F&F&4&1&2&7Hắc Long");
        assertTrue(b.ok(), String.valueOf(b.problem()));
        assertEquals("HacLong", b.name().key());
        assertEquals("Hắc Long", b.name().plain());
        assertTrue(b.name().legacy().startsWith("§x§f§f§4§1§2§7"), "hex as §x for holograms: " + b.name().legacy());
        assertEquals("&#ff4127Hắc Long", b.name().ampersand().toLowerCase().replace("hắc long", "Hắc Long"));

        Check c2 = check("<#FF4127>Hắc Long");
        assertTrue(c2.ok());
        assertEquals(b.name().legacy(), c2.name().legacy(), "<#RRGGBB> = &x&R&R&G&G&B&B = &#RRGGBB");
        assertEquals(b.name().legacy(), check("&#FF4127Hắc Long").name().legacy());
        assertEquals(b.name().legacy(), check("&x&f&f&4&1&2&7Hắc Long").name().legacy(), "lower case works too");

        Check c = check("&#55FFFFIce Team 2");
        assertTrue(c.ok());
        assertEquals("IceTeam2", c.name().key());
        assertEquals("Ice Team 2", c.name().plain());
        // #55FFFF is exactly &b: the serializer writes the short code
        assertEquals("&bIce Team 2", c.name().ampersand());
        assertEquals("&#ffaa01ice", check("&#FFAA01Ice").name().ampersand().toLowerCase());
    }

    /** Effective style of every text piece. */
    private static void leaves(net.kyori.adventure.text.Component c, net.kyori.adventure.text.format.Style parent,
                               java.util.Map<String, net.kyori.adventure.text.format.Style> out) {
        net.kyori.adventure.text.format.Style style = parent.merge(c.style(),
                net.kyori.adventure.text.format.Style.Merge.Strategy.ALWAYS);
        if (c instanceof net.kyori.adventure.text.TextComponent t && !t.content().isEmpty()) out.put(t.content(), style);
        for (net.kyori.adventure.text.Component child : c.children()) leaves(child, style, out);
    }

    @Test
    void nameStyleNeverLeaksIntoTheMessage() {
        for (String raw : List.of("&d&lTheHomie", "&dTheHomie&l", "&l&nThe&r&oHomie", "&x&F&F&5&5&F&F&l&oTheHomie",
                "<#FF55FF><bold>TheHomie")) {
            ClanName name = ClanNameFormatter.render(raw);
            net.kyori.adventure.text.Component msg = Text.parse(Text.fill(
                    Text.legacyToMini("&#55FF55Bạn đã tạo bang hội %clan%&#55FF55! (mã lệnh: %key%)"),
                    "clan", name.placeholder(), "key", name.key()));
            String plain = Text.plain(msg);
            assertFalse(plain.contains("<") || plain.contains(">"), "no tag printed as text: " + plain);
            java.util.Map<String, net.kyori.adventure.text.format.Style> styles = new java.util.LinkedHashMap<>();
            leaves(msg, net.kyori.adventure.text.format.Style.empty(), styles);
            for (var e : styles.entrySet()) {
                if (!e.getKey().contains("mã lệnh")) continue;
                for (TextDecoration d : TextDecoration.values()) {
                    assertTrue(e.getValue().decoration(d) != TextDecoration.State.TRUE,
                            raw + ": '" + e.getKey() + "' must not be " + d);
                }
                assertEquals(0x55FF55, e.getValue().color().value());
            }
            // the text right after the name, before any colour code, keeps the message colour
            net.kyori.adventure.text.Component after = Text.parse(Text.fill("<green>[%clan%] sau", "clan", name.placeholder()));
            java.util.Map<String, net.kyori.adventure.text.format.Style> s2 = new java.util.LinkedHashMap<>();
            leaves(after, net.kyori.adventure.text.format.Style.empty(), s2);
            var tail = s2.entrySet().stream().filter(x -> x.getKey().contains("sau")).findFirst().orElseThrow().getValue();
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.GREEN, tail.color(), raw);
            assertTrue(tail.decoration(TextDecoration.BOLD) != TextDecoration.State.TRUE, raw);
        }
        // the name itself keeps its own style
        ClanName name = ClanNameFormatter.render("&d&lTheHomie");
        java.util.Map<String, net.kyori.adventure.text.format.Style> own = new java.util.LinkedHashMap<>();
        leaves(Text.parse(name.mini()), net.kyori.adventure.text.format.Style.empty(), own);
        assertEquals(0xFF55FF, own.get("TheHomie").color().value());
        assertEquals(TextDecoration.State.TRUE, own.get("TheHomie").decoration(TextDecoration.BOLD));
    }

    @Test
    void sameKeyWhateverTheColoursAndAccents() {
        assertEquals(check("&cRồng Đỏ").name().key().toLowerCase(), check("&aRong Do").name().key().toLowerCase());
        assertEquals("RongDo", check("<#FF0000>Rồng Đỏ").name().key());
        assertEquals("RongDo", check("&x&F&F&0&0&0&0Rồng Đỏ").name().key());
        assertEquals("Duong", ClanNameFormatter.key("Đường"));
    }

    @Test
    void lengthAndCharacters() {
        assertEquals(Problem.TOO_SHORT, check("&cAb").problem());
        assertTrue(check("Abc").ok());
        assertTrue(check("&cAbcdefghijklmnop").ok(), "16 letters, colours not counted");
        assertEquals(Problem.TOO_LONG, check("Abcdefghijklmnopq").problem());
        assertEquals(Problem.BAD_CHARACTERS, check("Rồng@Đỏ").problem());
        assertEquals(Problem.BAD_CHARACTERS, check("Rồng  Đỏ").problem(), "double space");
        assertEquals(Problem.BAD_CHARACTERS, check("Team 😀").problem());
        assertEquals(Problem.BAD_CHARACTERS, check("a & b c").problem());
    }

    @Test
    void forbiddenFormats() {
        assertEquals(Problem.FORBIDDEN_FORMAT, check("&kRồng Đỏ").problem());
        assertEquals(Problem.FORBIDDEN_FORMAT, check("&mRồng Đỏ").problem());
        assertEquals(Problem.FORBIDDEN_FORMAT, check("<click:run_command:/op me>Rồng</click>").problem());
        assertEquals(Problem.FORBIDDEN_FORMAT, check("<hover:show_text:'x'>Rồng</hover>").problem());
        assertEquals(Problem.FORBIDDEN_FORMAT, check("<font:uniform>Rồng</font>").problem());
        assertEquals(Problem.FORBIDDEN_FORMAT, check("<obfuscated>Rồng</obfuscated>").problem());
        assertTrue(check("<bold><red>Rồng</red></bold> <#FFAA00>Đỏ").ok());
        assertTrue(check("<color:#FFAA00>Rồng</color>").ok());
        // gradient / rainbow are not allowed in clan names
        assertEquals(Problem.FORBIDDEN_FORMAT, check("<gradient:#FF4127:#FFC700>Hắc Long</gradient>").problem());
        assertEquals(Problem.FORBIDDEN_FORMAT, check("<rainbow>Hắc Long</rainbow>").problem());
        assertEquals(Problem.FORBIDDEN_FORMAT, check("<#FF0000>Hắc</gradient> Long").problem());
        // an incomplete &x colour is refused
        assertEquals(Problem.FORBIDDEN_FORMAT, check("&x&F&FHắc Long").problem());
        assertEquals(Problem.FORBIDDEN_FORMAT, check("&x&F&F&4&1&2&ZHắc Long").problem());
    }

    @Test
    void bannedWordsAreCheckedOnTheKey() {
        Check c = check("&cAd Min Team");
        assertEquals(Problem.BANNED_WORD, c.problem());
        assertEquals("admin", c.detail());
        assertEquals(Problem.BANNED_WORD, check("Stáff").problem(), "accents removed before the check");
    }

    @Test
    void stylePermissions() {
        Settings s = settings("boxclans.color.hex");
        Check refused = ClanNameFormatter.check("&#55FFFFIce", s, p -> false);
        assertEquals(Problem.STYLE_PERMISSION, refused.problem());
        assertEquals(Style.HEX, refused.style());
        assertEquals(Problem.STYLE_PERMISSION, ClanNameFormatter.check("<#55FFFF>Ice", s, p -> false).problem());
        Check x = ClanNameFormatter.check("&x&5&5&F&F&F&FIce", s, p -> false);
        assertEquals(Problem.STYLE_PERMISSION, x.problem());
        assertEquals(Style.HEX, x.style(), "&x needs the hex permission, its digits are not plain colours");
        assertTrue(ClanNameFormatter.check("&#55FFFFIce", s, Set.of("boxclans.color.hex")::contains).ok());
        assertTrue(ClanNameFormatter.check("&bIce", s, p -> false).ok(), "plain colours need no permission");
    }

    @Test
    void renderNeverInterpretsUnsafeTags() {
        ClanName n = ClanNameFormatter.render("<click:run_command:/op me>X</click>");
        assertNotEquals("X", n.plain());
    }
}
