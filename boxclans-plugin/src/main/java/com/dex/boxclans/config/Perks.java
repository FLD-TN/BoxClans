package com.dex.boxclans.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Immutable snapshot of {@code perks.yml} (§6, §8.3). */
public record Perks(int defaultMaxMembers, List<Tier> memberLimits, int defaultBannerPatterns, List<Tier> bannerPatterns) {

    public static final String FILE = "perks.yml";

    /** One permission tier; {@code amount -1} = unlimited. */
    public record Tier(String permission, int amount) {}

    public static Perks from(YamlConfiguration yaml) {
        return new Perks(
                (int) YamlFiles.integer(yaml, "default-max-members", 8),
                tiers(yaml.getMapList("member-limits")),
                (int) YamlFiles.integer(yaml, "default-banner-patterns", 6),
                tiers(yaml.getMapList("banner-patterns")));
    }

    private static List<Tier> tiers(List<Map<?, ?>> raw) {
        List<Tier> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            Object perm = m.get("permission");
            Object amount = m.get("amount");
            if (perm == null || !(amount instanceof Number n)) continue;
            out.add(new Tier(String.valueOf(perm), n.intValue()));
        }
        return List.copyOf(out);
    }

    /** Prefix of the numeric permission, e.g. {@code boxclans.members.} for {@code boxclans.members.20}. */
    public String numericPrefix() {
        return "boxclans.members.";
    }

    /**
     * Largest {@code <prefix><n>} among the granted permissions (n a positive whole number), 0 if none.
     * Named tiers like {@code boxclans.members.vip} are ignored here (they come from the tier list).
     */
    public static int numericLimit(Collection<String> granted, String prefix) {
        int best = 0;
        for (String perm : granted) {
            if (!perm.regionMatches(true, 0, prefix, 0, prefix.length())) continue;
            String tail = perm.substring(prefix.length());
            if (tail.isEmpty() || tail.length() > 6) continue;
            boolean digits = true;
            for (int i = 0; i < tail.length(); i++) if (!Character.isDigit(tail.charAt(i))) digits = false;
            if (digits) best = Math.max(best, Integer.parseInt(tail));
        }
        return best;
    }

    /** Member limit for a leader holding the permissions {@code has} accepts: the largest tier, -1 wins. */
    public int memberLimit(Predicate<String> has) {
        return best(memberLimits, defaultMaxMembers, has);
    }

    /** Banner layers allowed for a player. */
    public int bannerPatterns(Predicate<String> has) {
        return best(bannerPatterns, defaultBannerPatterns, has);
    }

    private static int best(List<Tier> tiers, int fallback, Predicate<String> has) {
        int best = fallback;
        for (Tier t : tiers) {
            if (!has.test(t.permission())) continue;
            if (t.amount() < 0) return -1;
            best = Math.max(best, t.amount());
        }
        return best;
    }
}
