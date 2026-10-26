package com.dex.boxclans.gui;

import com.dex.boxclans.config.Settings;
import com.dex.boxclans.points.KillEventManager;
import com.dex.boxclans.points.PointSource;
import com.dex.boxclans.points.PointsConfig;
import com.dex.boxclans.text.Text;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongBiFunction;

/**
 * Lore of the "ᴄʟᴀɴ ꜱᴄᴏʀᴇ" button (§8.4), generated from {@code points.yml}: one line per enabled source (or its
 * own {@code lore:}), the daily cap with today's progress, every-N counts, the running kill event and the rule C
 * warning. Lines are returned filled ({@link Text.Mini}).
 */
public final class ScoreLore {

    /** The three list placeholders of the lore. */
    public record Lines(List<Text.Mini> sources, List<Text.Mini> killEvent, List<Text.Mini> warnings) {}

    private ScoreLore() {
    }

    /**
     * @param score  the {@code score} section of {@code menus/settings.yml}
     * @param today  points earned today by the clan from a source
     * @param window rule C window, formatted
     */
    public static Lines build(ConfigurationSection score, PointsConfig points, KillEventManager.Active event,
                              Settings.AntiFarm antiFarm, String window, UUID clan,
                              ToLongBiFunction<UUID, String> today) {
        List<Text.Mini> sources = new ArrayList<>();
        for (Map.Entry<String, PointSource> e : points.sources().entrySet()) {
            PointSource src = e.getValue();
            if (!src.enabled()) continue;
            if (src.lore() != null && src.lore().isEmpty()) continue;
            String template;
            if (src.lore() != null) {
                template = src.lore();
            } else if (src.points() > 0) {
                template = string(score, "source-positive");
            } else if (src.points() < 0) {
                template = string(score, "source-negative");
            } else {
                template = string(score, "source-zero");
            }
            List<Object> ph = new ArrayList<>(List.of("display", new Text.Mini(Text.legacyToMini(src.display())),
                    "points", src.points(), "every", src.every()));
            for (PointSource other : points.sources().values()) {
                ph.add("points_" + other.id());
                ph.add(other.points());
            }
            sources.add(mini(template, ph.toArray()));
            if (src.every() > 1) sources.add(mini(string(score, "every-line"), "every", src.every()));
            if (src.clanDailyCap() > 0) {
                for (String line : score.getStringList("cap-lines")) {
                    sources.add(mini(line, "cap", src.clanDailyCap(), "today", today.applyAsLong(clan, src.id())));
                }
            }
        }
        List<Text.Mini> kill = new ArrayList<>();
        if (event != null) {
            for (String line : score.getStringList("kill-event")) {
                kill.add(mini(line, "event", new Text.Mini(Text.legacyToMini(event.profile().display())),
                        "kill", event.profile().kill(), "death", event.profile().death()));
            }
        }
        List<Text.Mini> warnings = new ArrayList<>();
        if (antiFarm.pairWindow() > 0) {
            for (String line : score.getStringList("warnings")) {
                warnings.add(mini(line, "max", antiFarm.pairMaxKills(), "window", window));
            }
        }
        return new Lines(sources, kill, warnings);
    }

    private static String string(ConfigurationSection s, String path) {
        String v = s.getString(path);
        return v == null ? "" : v;
    }

    private static Text.Mini mini(String template, Object... ph) {
        return new Text.Mini(Text.fill(Text.legacyToMini(template), ph));
    }
}
