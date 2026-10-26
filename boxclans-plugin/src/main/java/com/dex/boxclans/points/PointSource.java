package com.dex.boxclans.points;

/**
 * One source of {@code points.yml} (§5.1).
 *
 * @param lore line for the "ᴄʟᴀɴ ꜱᴄᴏʀᴇ" lore; {@code null} = generated, {@code ""} = hidden
 */
public record PointSource(String id, long points, boolean enabled, String display, int every, long cooldownSeconds,
                          long dailyCap, long clanDailyCap, boolean boost, boolean contribution, boolean announce,
                          String lore) {

    /** A source made on the fly for admin commands with an undeclared id ({@code admin}). */
    public static PointSource adhoc(String id) {
        return new PointSource(id, 0, true, id, 1, 0, 0, 0, false, true, true, null);
    }
}
