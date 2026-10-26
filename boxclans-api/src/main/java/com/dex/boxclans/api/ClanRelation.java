package com.dex.boxclans.api;

/** Relation between two players (§3). */
public enum ClanRelation {
    /** Both UUIDs are the same player. */
    SELF,
    /** Both players are members of the same clan. */
    SAME_CLAN,
    /** Different clans, or at least one of them has no clan. */
    NONE
}
