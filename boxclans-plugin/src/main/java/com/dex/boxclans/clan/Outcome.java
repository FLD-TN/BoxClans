package com.dex.boxclans.clan;

/** Result of a {@link ClanManager} action; the command and GUI layers turn it into a message. */
public enum Outcome {
    OK,
    /** The actor has no clan. */
    NOT_IN_CLAN,
    /** The actor (or the joining player) already has a clan. */
    ALREADY_IN_CLAN,
    /** The target already has a clan (invite). */
    TARGET_IN_CLAN,
    /** The target is not a member of the actor's clan. */
    TARGET_NOT_MEMBER,
    /** The actor's role lacks the permission. */
    NO_PERMISSION,
    /** Only the Minh Chủ can do this. */
    NOT_LEADER,
    /** Another clan has the same command key. */
    NAME_TAKEN,
    /** The clan reached its member limit. */
    CLAN_FULL,
    /** The clan reached its pending invite limit. */
    INVITE_LIMIT,
    ALREADY_INVITED,
    NO_INVITE,
    /** The actor cannot act on this member (Phó Bang kicking a Phó Bang / the Minh Chủ). */
    TARGET_HIGHER,
    /** The target is the actor. */
    SELF,
    /** The Minh Chủ must transfer or disband instead. */
    LEADER_CANNOT_LEAVE,
    /** The target is the Minh Chủ (demote / kick). */
    TARGET_IS_LEADER,
    SAME_ROLE,
    /** The role is disabled ({@code max: 0}). */
    ROLE_DISABLED,
    /** A confirmation screen is required first (swap Phó Bang, transfer leader). */
    NEEDS_CONFIRM,
    /** The clan does not exist (any more). */
    UNKNOWN_CLAN,
    /** Rename: only colours may change before the cooldown ends. */
    RENAME_COOLDOWN,
    /** Rename: the new name equals the current one. */
    SAME_NAME,
    /** Another plugin cancelled the change (it tells the player why). */
    CANCELLED
}
