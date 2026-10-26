package com.dex.boxclans.api;

import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A read-only snapshot of a clan (§3). A snapshot never changes; ask {@link ClanService} again for fresh data.
 * Safe to use from any thread.
 */
public interface Clan {

    /** Internal key, never changes (renames keep it). */
    UUID uuid();

    /** Command key generated from the name: colours, accents and spaces removed, e.g. {@code RongDo} (§4). */
    String key();

    /** Coloured name as legacy text ({@code §x} hex), for scoreboards and holograms. */
    String name();

    /** Coloured name as an Adventure component, for chat and GUIs. */
    Component nameComponent();

    /** Name without colours, e.g. {@code Rồng Đỏ}. */
    String plainName();

    /** Coloured name with {@code &} codes, e.g. {@code &c&lRồng &#FFAA00Đỏ}, for plugins that translate {@code &}. */
    String nameAmpersand();

    /** Description, empty when not set. */
    String description();

    /** The Minh Chủ. */
    UUID leader();

    /** The (first) Phó Bang, if any. */
    Optional<UUID> vice();

    /** Every member, the leader included. */
    Set<UUID> members();

    /** Role of a member, {@code null} if the player is not a member. */
    ClanRole roleOf(UUID player);

    /** Clan points. */
    long points();

    /** Points contributed by one member, 0 if unknown. */
    long contribution(UUID member);

    /** Member limit from the leader's rank (§6), {@code -1} = unlimited. */
    int maxMembers();

    /** Creation time, epoch milliseconds. */
    long createdAt();

    /** Whether members may damage each other (§4C). */
    boolean friendlyFire();

    /** Id of the shield icon in use (BoxClans {@code icons.yml}, e.g. {@code royal}), empty when none. */
    default String icon() {
        return "";
    }

    /** Ids of the shield icons the clan owns (bought or unlocked). */
    default Set<String> ownedIcons() {
        return Set.of();
    }
}
