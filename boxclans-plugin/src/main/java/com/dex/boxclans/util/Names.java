package com.dex.boxclans.util;

import com.dex.boxclans.clan.ClanData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Player names ↔ UUIDs without blocking lookups (online players, then the server's user cache). */
public final class Names {

    private Names() {
    }

    /** Last known name, or the first 8 characters of the UUID. */
    public static String of(UUID player) {
        if (player == null) return "";
        Player online = Bukkit.getPlayer(player);
        if (online != null) return online.getName();
        String name = Bukkit.getOfflinePlayer(player).getName();
        return name != null ? name : player.toString().substring(0, 8);
    }

    /** Online player by exact name (case-insensitive). */
    public static Player online(String name) {
        return name == null ? null : Bukkit.getPlayerExact(name);
    }

    /** Online player, else a player known to the server; {@code null} if unknown. */
    public static OfflinePlayer known(String name) {
        Player online = online(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    /** A member of the clan by name (case-insensitive), online or not. */
    public static UUID member(ClanData clan, String name) {
        if (clan == null || name == null) return null;
        for (ClanData.Member m : clan.members()) {
            if (of(m.player()).equalsIgnoreCase(name)) return m.player();
        }
        return null;
    }

    public static List<String> of(Collection<UUID> players) {
        List<String> out = new ArrayList<>(players.size());
        for (UUID p : players) out.add(of(p));
        return out;
    }

    /** Online members of a clan. */
    public static List<Player> online(ClanData clan) {
        List<Player> out = new ArrayList<>();
        for (ClanData.Member m : clan.members()) {
            Player p = Bukkit.getPlayer(m.player());
            if (p != null) out.add(p);
        }
        return out;
    }
}
