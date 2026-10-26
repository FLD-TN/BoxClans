package com.dex.boxclans;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P11 "Gỡ BoxCore thì BoxClans vẫn chạy": BoxCore and PlaceholderAPI are imported by exactly one class each, and
 * the plugin only touches those classes after checking the plugin is enabled. The test classpath has neither
 * API (compileOnly), so every other test also proves the rest of the plugin runs without them.
 */
class OptionalDependenciesTest {

    private static List<String> importers(String pkg) throws IOException {
        Path root = Path.of("src/main/java");
        List<String> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (Files.readString(f, StandardCharsets.UTF_8).contains("import " + pkg)) {
                    out.add(root.relativize(f).toString().replace('\\', '/'));
                }
            }
        }
        return out;
    }

    @Test
    void oneClassPerOptionalApi() throws IOException {
        // 1.3.0: the clan boost hook is the second (and last) class allowed to import BoxCore
        assertEquals(List.of("com/dex/boxclans/hook/BoxCoreClansHook.java", "com/dex/boxclans/hook/BoxCoreHook.java"),
                importers("com.dex.boxcore").stream().sorted().toList());
        assertEquals(List.of("com/dex/boxclans/hook/PlaceholderHook.java"), importers("me.clip.placeholderapi"));
        assertEquals(List.of(), importers("dev.lone"), "ItemsAdder is reached through reflection only");
    }

    @Test
    void apisAreNotOnTheTestClasspath() {
        assertTrue(absent("com.dex.boxcore.api.BoostService"), "BoxCore API must stay compileOnly");
        assertTrue(absent("me.clip.placeholderapi.expansion.PlaceholderExpansion"), "PAPI must stay compileOnly");
    }

    @Test
    void pluginGuardsTheHooks() throws IOException {
        String main = Files.readString(Path.of("src/main/java/com/dex/boxclans/BoxClansPlugin.java"), StandardCharsets.UTF_8);
        assertTrue(main.contains("isPluginEnabled(\"BoxCore\")) hookBoxCore();"));
        assertTrue(main.contains("isPluginEnabled(\"BoxCore\")) hookClanBoosts();"));
        assertTrue(main.contains("isPluginEnabled(\"PlaceholderAPI\")) registerPlaceholders();"));
        assertTrue(main.contains("isPluginEnabled(\"ItemsAdder\")) hookItemsAdder();"));
        String yml = Files.readString(Path.of("src/main/resources/plugin.yml"), StandardCharsets.UTF_8);
        assertTrue(yml.contains("softdepend: [PlaceholderAPI, BoxCore, LuckPerms, ItemsAdder]"));
        assertTrue(!yml.contains("\ndepend:"), "no hard dependency");
    }

    private static boolean absent(String className) {
        try {
            Class.forName(className, false, OptionalDependenciesTest.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException ex) {
            return true;
        }
    }
}
