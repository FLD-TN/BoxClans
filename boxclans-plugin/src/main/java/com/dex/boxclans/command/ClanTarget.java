package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.util.Names;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The target of a staff command, always written with its kind (BOXCLANS_SHOP_SPEC.md §2.2):
 * {@code clan <mã bang hội>} or {@code player <tên>} (the clan the player is in). Never guessed, so a clan whose key
 * equals a player name is never hit by mistake.
 */
final class ClanTarget {

    static final String CLAN = "clan";
    static final String PLAYER = "player";
    static final List<String> KINDS = List.of(CLAN, PLAYER);

    private ClanTarget() {
    }

    /**
     * Resolves {@code <kind> <value>}; on failure the sender is told why and {@code null} is returned.
     */
    static ClanData resolve(BoxClansPlugin plugin, CommandSender sender, String kind, String value) {
        String k = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        if (k.equals(CLAN)) {
            ClanSnapshot snap = KeyArgs.clan(plugin, value);
            ClanData clan = snap == null ? null : plugin.manager().clan(snap.uuid());
            if (clan == null) plugin.messages().send(sender, "clan-not-found", "clan", value);
            return clan;
        }
        if (k.equals(PLAYER)) {
            OfflinePlayer target = Names.known(value);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found", "player", value);
                return null;
            }
            ClanData clan = plugin.manager().of(target.getUniqueId());
            if (clan == null) {
                plugin.messages().send(sender, "target-player-no-clan",
                        "player", target.getName() == null ? value : target.getName());
            }
            return clan;
        }
        plugin.messages().send(sender, "target-kind", "value", kind == null ? "" : kind);
        return null;
    }

    /** Tab completion of the value after {@code kind}. */
    static List<String> complete(BoxClansPlugin plugin, String kind, String prefix) {
        String k = kind.toLowerCase(Locale.ROOT);
        if (k.equals(CLAN)) return KeyArgs.filter(KeyArgs.keys(plugin), prefix);
        if (k.equals(PLAYER)) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return KeyArgs.filter(names, prefix);
        }
        return List.of();
    }
}
