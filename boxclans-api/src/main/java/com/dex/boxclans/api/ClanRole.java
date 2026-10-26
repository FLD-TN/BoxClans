package com.dex.boxclans.api;

import java.util.Locale;
import java.util.Optional;

/**
 * The three roles of a clan (§4A). The keys are fixed and used in commands and by other plugins; display name
 * and colour come from {@code roles.yml} through {@link ClanService#roleDisplay} and {@link ClanService#roleColor}.
 */
public enum ClanRole {
    /** Minh Chủ: exactly one per clan, all permissions. */
    LEADER("MinhChu"),
    /** Phó Bang: at most {@code max} (default 1). */
    VICE("PhoBang"),
    /** Thành Viên: the default role. */
    MEMBER("ThanhVien");

    private final String key;

    ClanRole(String key) {
        this.key = key;
    }

    /** The fixed command key: {@code MinhChu}, {@code PhoBang} or {@code ThanhVien}. */
    public String key() {
        return key;
    }

    /** Looks a role up by its key, case-insensitive. */
    public static Optional<ClanRole> byKey(String key) {
        if (key == null) return Optional.empty();
        String k = key.toLowerCase(Locale.ROOT);
        for (ClanRole role : values()) {
            if (role.key.toLowerCase(Locale.ROOT).equals(k)) return Optional.of(role);
        }
        return Optional.empty();
    }
}
