package com.dex.boxclans.config;

import com.dex.boxclans.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable snapshot of {@code messages.yml} (§8.1, §8.2). Three kinds of keys:
 * <ul>
 *   <li>a <b>message</b>: a section {@code {chat: {enabled, text, prefix}, actionbar: {enabled, text}}};
 *       {@code text} may be a list (one chat line each);</li>
 *   <li>a <b>text</b>: a plain string (labels, words used inside other messages);</li>
 *   <li>a <b>list</b>: a list of strings (help pages, lore blocks).</li>
 * </ul>
 * Every template is converted to MiniMessage once here; a key missing from the file comes from the jar copy.
 */
public final class Messages {

    public static final String FILE = "messages.yml";

    /** One message: chat and actionbar templates (MiniMessage), {@code null} when that channel is off. */
    public record Entry(String chat, boolean prefixed, String actionbar) {}

    private final Map<String, Entry> entries;
    private final Map<String, String> texts;
    private final Map<String, List<String>> lists;
    private final String prefix;

    private Messages(Map<String, Entry> entries, Map<String, String> texts, Map<String, List<String>> lists) {
        this.entries = entries;
        this.texts = texts;
        this.lists = lists;
        this.prefix = texts.getOrDefault("prefix", "");
    }

    /** Builds the snapshot from a file loaded by {@link YamlFiles} (bundled copy attached as defaults). */
    public static Messages from(YamlConfiguration yaml) {
        Set<String> keys = new TreeSet<>(yaml.getKeys(true));
        if (yaml.getDefaults() != null) keys.addAll(yaml.getDefaults().getKeys(true));
        Map<String, Entry> entries = new HashMap<>();
        Map<String, String> texts = new HashMap<>();
        Map<String, List<String>> lists = new HashMap<>();
        List<String> messageSections = new ArrayList<>();
        for (String key : keys) {
            if (insideAny(key, messageSections)) continue;
            if (yaml.isConfigurationSection(key)) {
                ConfigurationSection section = yaml.getConfigurationSection(key);
                if (section != null && (section.isConfigurationSection("chat") || section.isConfigurationSection("actionbar"))) {
                    messageSections.add(key + ".");
                    entries.put(key, entry(section));
                }
            } else if (yaml.isList(key)) {
                lists.put(key, convert(yaml.getStringList(key)));
            } else {
                texts.put(key, Text.legacyToMini(YamlFiles.string(yaml, key, "")));
            }
        }
        return new Messages(Map.copyOf(entries), Map.copyOf(texts), Map.copyOf(lists));
    }

    private static boolean insideAny(String key, Collection<String> prefixes) {
        for (String p : prefixes) if (key.startsWith(p)) return true;
        return false;
    }

    private static Entry entry(ConfigurationSection section) {
        String chat = null;
        boolean prefixed = true;
        ConfigurationSection c = section.getConfigurationSection("chat");
        if (c != null && YamlFiles.bool(c, "enabled", true)) {
            chat = textOf(c);
            prefixed = YamlFiles.bool(c, "prefix", true);
        }
        String actionbar = null;
        ConfigurationSection a = section.getConfigurationSection("actionbar");
        if (a != null && YamlFiles.bool(a, "enabled", false)) actionbar = textOf(a);
        if (chat != null && chat.isEmpty()) chat = null;
        if (actionbar != null && actionbar.isEmpty()) actionbar = null;
        return new Entry(chat, prefixed, actionbar);
    }

    private static String textOf(ConfigurationSection channel) {
        if (channel.isList("text")) {
            List<String> lines = convert(channel.getStringList("text"));
            return String.join("<newline>", lines);
        }
        return Text.legacyToMini(YamlFiles.string(channel, "text", ""));
    }

    private static List<String> convert(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(Text.legacyToMini(line));
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ lookups

    public boolean has(String key) {
        return entries.containsKey(key) || texts.containsKey(key) || lists.containsKey(key);
    }

    /** The MiniMessage text for a text key, {@code ""} if unknown. */
    public String text(String key) {
        return texts.getOrDefault(key, "");
    }

    /** The text key rendered with placeholders. */
    public Component textComponent(String key, Object... placeholders) {
        return render(text(key), placeholders);
    }

    /** The MiniMessage lines for a list key, empty if unknown. */
    public List<String> list(String key) {
        return lists.getOrDefault(key, List.of());
    }

    public Entry entry(String key) {
        return entries.get(key);
    }

    public String prefix() {
        return prefix;
    }

    /** Renders a MiniMessage template with placeholders. */
    public Component render(String template, Object... placeholders) {
        return Text.parse(Text.fill(template, placeholders));
    }

    /** The chat line of a message (with prefix), {@code null} when the chat channel is off or unknown. */
    public Component chat(String key, Object... placeholders) {
        Entry e = entries.get(key);
        if (e == null || e.chat() == null) return null;
        return render((e.prefixed() ? prefix : "") + e.chat(), placeholders);
    }

    // ------------------------------------------------------------------ sending

    /** Sends a message on its enabled channels; the actionbar only reaches players. */
    public void send(CommandSender to, String key, Object... placeholders) {
        if (to == null) return;
        Entry e = entries.get(key);
        if (e == null) return;
        if (e.chat() != null) to.sendMessage(render((e.prefixed() ? prefix : "") + e.chat(), placeholders));
        if (e.actionbar() != null && to instanceof Player player) {
            player.sendActionBar(render(e.actionbar(), placeholders));
        }
    }

    /** Sends a message to several receivers (rendered once). */
    public void send(Collection<? extends CommandSender> to, String key, Object... placeholders) {
        Entry e = entries.get(key);
        if (e == null || to.isEmpty()) return;
        Component chat = e.chat() == null ? null : render((e.prefixed() ? prefix : "") + e.chat(), placeholders);
        Component bar = e.actionbar() == null ? null : render(e.actionbar(), placeholders);
        for (CommandSender s : to) {
            if (chat != null) s.sendMessage(chat);
            if (bar != null && s instanceof Player p) p.sendActionBar(bar);
        }
    }

    /** Sends each line of a list key without prefix. */
    public void sendList(CommandSender to, String key, Object... placeholders) {
        if (to == null) return;
        for (String line : list(key)) to.sendMessage(render(line, placeholders));
    }
}
