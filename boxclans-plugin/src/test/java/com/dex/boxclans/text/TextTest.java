package com.dex.boxclans.text;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TextTest {

    @Test
    void legacyCodesBecomeMiniMessage() {
        assertEquals("<red><bold>Rồng <!bold><gold>Đỏ", Text.legacyToMini("&c&lRồng &6Đỏ"));
        assertEquals("<#FFAA00>x", Text.legacyToMini("&#FFAA00x"));
        assertEquals("<#FFAA00>x", Text.legacyToMini("§x§F§F§A§A§0§0x"));
        assertEquals("a & b", Text.legacyToMini("a & b"));
        assertEquals("<yellow>&c&lRồng", Text.legacyToMini("&e&&c&&lRồng"));
        assertEquals("<bold>A<reset> <gray>B", Text.legacyToMini("&lA&r &7B"));
        assertEquals("<gradient:#FF0000:#FFAA00>x</gradient>", Text.legacyToMini("<gradient:#FF0000:#FFAA00>x</gradient>"));
    }

    @Test
    void colourCodesKeepClickAndHover() {
        Component c = Text.colorize("<click:run_command:/clan settings>&cBạn &lkhông &7thể</click>");
        assertEquals("Bạn không thể", Text.plain(c));
        java.util.List<String> withoutClick = new java.util.ArrayList<>();
        leaves(c, null, withoutClick);
        assertEquals(java.util.List.of(), withoutClick, "every word keeps the click");
    }

    private static void leaves(Component c, net.kyori.adventure.text.event.ClickEvent inherited, java.util.List<String> out) {
        net.kyori.adventure.text.event.ClickEvent click = c.clickEvent() != null ? c.clickEvent() : inherited;
        if (c instanceof net.kyori.adventure.text.TextComponent t && !t.content().isEmpty() && click == null) {
            out.add(t.content());
        }
        for (Component child : c.children()) leaves(child, click, out);
    }

    @Test
    void placeholderValuesCannotInjectTags() {
        String filled = Text.fill("<yellow>%message%", "message", "<red>hack &c");
        Component c = Text.parse(filled);
        assertEquals("<red>hack &c", Text.plain(c));
    }

    @Test
    void componentValuesKeepTheirStyle() {
        Component name = Text.colorize("&cRồng");
        String filled = Text.fill("Clan %clan%", "clan", name);
        assertEquals("Clan Rồng", Text.plain(Text.parse(filled)));
        assertFalse(Text.legacy(Text.parse(filled)).equals("Clan Rồng"));
    }
}
