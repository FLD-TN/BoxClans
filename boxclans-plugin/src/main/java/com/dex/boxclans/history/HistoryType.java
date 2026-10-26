package com.dex.boxclans.history;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * The kinds of clan history entries (BOXCLANS_SHOP_SPEC.md §6.1), stored by name in {@code clan_history.type}. Never
 * rename a constant: old rows keep the old name.
 *
 * <p>Meaning of the columns per type: {@code actor} did it ({@code null} = staff / console, then
 * {@code actor_name} may hold the command sender), {@code target} is the other player, {@code amount} and
 * {@code detail} as written next to each constant.
 */
public enum HistoryType {
    /** Clan created. detail = name as typed. */
    CREATE(Group.CLAN),
    /** A player joined (accepted an invite). */
    JOIN(Group.MEMBERS),
    /** A member left. */
    LEAVE(Group.MEMBERS),
    /** target was kicked by actor. */
    KICK(Group.MEMBERS),
    /** target was kicked at random because the clan went over its member limit. */
    KICK_OVER_LIMIT(Group.MEMBERS),
    /** target's role changed. detail = {@code OLD>NEW} role keys. */
    ROLE(Group.MEMBERS),
    /** actor (the old Minh Chủ) handed the clan to target. amount = 1 when done by staff. */
    LEADER(Group.MEMBERS),
    /** Renamed. detail = old raw name, {@link #SEPARATOR}, new raw name. */
    RENAME(Group.CLAN),
    /** Shield icon changed. detail = old id, {@link #SEPARATOR}, new id ("" = none). */
    ICON(Group.CLAN),
    /** PvP in the clan turned on (amount 1) or off (amount 0). */
    PVP(Group.CLAN),
    /** Minh Huy added. amount = how much, detail = reason. */
    MINHHUY_ADD(Group.MINHHUY),
    /** Minh Huy removed by staff. amount = how much, detail = reason. */
    MINHHUY_TAKE(Group.MINHHUY),
    /** Minh Huy set by staff. amount = the new balance, detail = reason. */
    MINHHUY_SET(Group.MINHHUY),
    /** Minh Huy reset to 0. amount = the old balance. */
    MINHHUY_RESET(Group.MINHHUY),
    /** Bought in the shop. amount = price, detail = item name. */
    SHOP_BUY(Group.SHOP),
    /** Taken out of the storage. detail = item name. */
    VAULT_WITHDRAW(Group.VAULT),
    /** Used from the storage (clan boost). detail = item name. */
    VAULT_USE(Group.VAULT),
    /** Given to the storage by staff. amount = quantity, detail = item name. */
    VAULT_GIVE(Group.VAULT),
    /** Removed from the storage by staff. amount = quantity, detail = item name. */
    VAULT_REMOVE(Group.VAULT),
    /**
     * Clan boost started (written from BoxCore's event, whatever caused it). For every BOOST_ type: amount = multiplier
     * in tenths, detail = type id, {@link #SEPARATOR}, seconds added, {@link #SEPARATOR}, replaced multiplier (tenths).
     */
    BOOST_START(Group.BOOST),
    /** Clan boost extended. */
    BOOST_EXTEND(Group.BOOST),
    /** Clan boost replaced by a higher one. */
    BOOST_REPLACE(Group.BOOST),
    /** Clan boost stopped by staff. */
    BOOST_STOP(Group.BOOST),
    /** Clan boost ran out. */
    BOOST_END(Group.BOOST);

    /** Separates the two values of {@link #RENAME} and {@link #ICON} in {@code detail}. */
    public static final String SEPARATOR = "\u001F";

    /** The filters of the history menu. */
    public enum Group { MEMBERS, MINHHUY, SHOP, VAULT, BOOST, CLAN }

    private final Group group;

    HistoryType(Group group) {
        this.group = group;
    }

    public Group group() {
        return group;
    }

    /** Stored name. */
    public String key() {
        return name();
    }

    /** The type of a stored name, {@code null} when unknown (written by a newer version). */
    public static HistoryType of(String key) {
        if (key == null) return null;
        try {
            return valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Every type of a group. */
    public static Set<HistoryType> of(Group group) {
        Set<HistoryType> out = EnumSet.noneOf(HistoryType.class);
        for (HistoryType t : values()) if (t.group == group) out.add(t);
        return out;
    }
}
