package com.dex.boxclans.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Locale;

/** Immutable snapshot of {@code config.yml}. */
public record Settings(
        int nameMinLength,
        int nameMaxLength,
        List<String> bannedWords,
        long renameCooldownSeconds,
        String permColors,
        String permFormats,
        String permHex,
        int descriptionMaxLength,
        int promptTimeoutSeconds,
        List<String> cancelWords,
        long inviteExpirySeconds,
        int inviteMaxPending,
        String clanChatFormat,
        String spyFormat,
        String chatShortcut,
        boolean defaultFriendlyFire,
        long friendlyFireMessageCooldownSeconds,
        String flagDefaultMaterial,
        String noneValue,
        String relationColorSame,
        String relationColorNone,
        AntiFarm antiFarm,
        Backup backup,
        int historyKeepDays) {

    public static final String FILE = "config.yml";

    /** §7. Windows in seconds, 0 = rule off. */
    public record AntiFarm(boolean sameIp, long networkWindow, long pairWindow, int pairMaxKills,
                           boolean notifyStaff, boolean logFile) {}

    /** Daily copy of {@code clans.db} into {@code backups/}; the newest {@code keep} copies are kept. */
    public record Backup(boolean enabled, int keep) {}

    public static Settings from(YamlConfiguration y) {
        AntiFarm antiFarm = new AntiFarm(
                YamlFiles.bool(y, "anti-farm.same-ip", true),
                Math.max(0, YamlFiles.integer(y, "anti-farm.network-window", 600)),
                Math.max(0, YamlFiles.integer(y, "anti-farm.pair-window", 600)),
                Math.max(1, (int) YamlFiles.integer(y, "anti-farm.pair-max-kills", 3)),
                YamlFiles.bool(y, "anti-farm.notify-staff", true),
                YamlFiles.bool(y, "anti-farm.log-file", true));
        int min = Math.max(1, (int) YamlFiles.integer(y, "clan-name.min-length", 3));
        return new Settings(
                min,
                Math.max(min, (int) YamlFiles.integer(y, "clan-name.max-length", 16)),
                lower(y.getStringList("clan-name.banned-words")),
                Math.max(0, YamlFiles.integer(y, "clan-name.rename-cooldown", 1800)),
                YamlFiles.string(y, "clan-name.color-permissions.colors", ""),
                YamlFiles.string(y, "clan-name.color-permissions.formats", ""),
                YamlFiles.string(y, "clan-name.color-permissions.hex", ""),
                descriptionMax(YamlFiles.integer(y, "clan-description.max-length", 50)),
                Math.max(5, (int) YamlFiles.integer(y, "prompt.timeout", 60)),
                lower(y.getStringList("prompt.cancel-words")),
                Math.max(10, YamlFiles.integer(y, "invite.expiry", 300)),
                Math.max(1, (int) YamlFiles.integer(y, "invite.max-pending", 10)),
                current(y, "chat.clan-format", LEGACY_CLAN_FORMAT),
                current(y, "chat.spy-format", LEGACY_SPY_FORMAT),
                YamlFiles.string(y, "chat.shortcut-prefix", "!"),
                YamlFiles.bool(y, "friendly-fire.default", false),
                Math.max(0, YamlFiles.integer(y, "friendly-fire.message-cooldown", 3)),
                YamlFiles.string(y, "flag.default-material", "WHITE_BANNER"),
                YamlFiles.string(y, "placeholders.none-value", ""),
                YamlFiles.string(y, "placeholders.relation-colors.same", "&a"),
                YamlFiles.string(y, "placeholders.relation-colors.none", "&c"),
                antiFarm,
                new Backup(YamlFiles.bool(y, "backup.enabled", true),
                        Math.max(1, (int) YamlFiles.integer(y, "backup.keep", 7))),
                (int) Math.max(1, Math.min(3650, YamlFiles.integer(y, "history.keep-days", 30))));
    }

    /** 1.0.0 defaults of the chat formats (small caps), replaced by the 1.1.0 wording when left unchanged. */
    static final String LEGACY_CLAN_FORMAT = "&#FFC700ᴄʟᴀɴ &7| %role_color%(%role%) %player%&7: &e%message%";
    static final String LEGACY_SPY_FORMAT = "&#FF5555ꜱᴘʏ &7| %clan% %role_color%(%role%) %player%&7: &e%message%";

    /** The file value, or the bundled default when the file still holds the unchanged 1.0.0 default. */
    private static String current(YamlConfiguration y, String path, String legacyDefault) {
        String value = YamlFiles.string(y, path, "");
        if (!value.equals(legacyDefault) || y.getDefaults() == null) return value;
        return YamlFiles.string(y.getDefaults(), path, value);
    }

    private static List<String> lower(List<String> in) {
        return in.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    /** {@code clan-description.max-length}: at least 1; 64, the bundled value before 1.4.3, is read as the new 50. */
    static int descriptionMax(long value) {
        if (value == 64) return 50;
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, value));
    }
}
