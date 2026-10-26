package com.dex.boxclans.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI expansion {@code boxclans} (§9): the only class importing PlaceholderAPI, loaded only when it is
 * installed. Values come from {@link PlaceholderValues}; may run on async threads.
 */
public final class PlaceholderHook extends PlaceholderExpansion implements Relational {

    private final PlaceholderValues values;
    private final String version;

    public PlaceholderHook(PlaceholderValues values, String version) {
        this.values = values;
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "boxclans";
    }

    @Override
    public String getAuthor() {
        return "Dex";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        try {
            return values.resolve(player == null ? null : player.getUniqueId(), params);
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    public String onPlaceholderRequest(Player one, Player two, String identifier) {
        try {
            return values.relational(one == null ? null : one.getUniqueId(), two == null ? null : two.getUniqueId(),
                    identifier);
        } catch (Throwable t) {
            return "";
        }
    }
}
