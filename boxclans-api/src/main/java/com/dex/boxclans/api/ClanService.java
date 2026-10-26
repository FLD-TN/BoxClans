package com.dex.boxclans.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to clans, registered in Bukkit's {@code ServicesManager} (§3).
 *
 * <p><b>Threads:</b> every method is safe from any thread (CrazyEnchantments calls {@link #isFriendly} off the
 * main thread) and never throws.
 */
public interface ClanService {

    Optional<Clan> clan(UUID clanUuid);

    /** By command key ({@link Clan#key()}), case-insensitive. */
    Optional<Clan> clanByKey(String key);

    /** The clan of a player. */
    Optional<Clan> clanOf(UUID player);

    Optional<ClanRole> roleOf(UUID player);

    /** Display name of a role from {@code roles.yml}, e.g. {@code Minh Chủ}. */
    String roleDisplay(ClanRole role);

    /** Legacy colour of a role from {@code roles.yml}, e.g. {@code §c}. */
    String roleColor(ClanRole role);

    /** Every clan. */
    Collection<Clan> clans();

    ClanRelation relation(UUID a, UUID b);

    /** True when both players are members of the same clan ({@code a != b}). */
    boolean sameClan(UUID a, UUID b);

    /**
     * True when {@code b} is an ally of {@code a} for CrazyEnchantments: same player or same clan. Clan members
     * are allies even when the clan has friendly fire on (§10.1).
     */
    boolean isFriendly(UUID a, UUID b);

    /** Clans ordered by points (highest first), at most {@code limit}. */
    List<Clan> top(int limit);

    /** 1-based rank of a clan by points, 0 if the clan does not exist. */
    int rank(UUID clanUuid);
}
