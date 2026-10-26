package com.dex.boxclans.clan;

import java.util.Locale;
import java.util.Optional;

/**
 * Role permissions that {@code roles.yml} can grant to Phó Bang and Thành Viên (§9). The Minh Chủ has them all;
 * changing roles, transferring the leader and disbanding are leader-only and not listed here.
 */
public enum RolePermission {
    INVITE_MEMBERS,
    KICK_MEMBERS,
    RENAME_CLAN,
    SET_DESCRIPTION,
    EDIT_FLAG,
    MODIFY_FRIENDLY_FIRE,
    USE_CLAN_CHAT;

    public static Optional<RolePermission> parse(String name) {
        if (name == null) return Optional.empty();
        try {
            return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
