package com.dex.boxclans.chat;

import com.dex.boxclans.storage.Database;
import com.dex.boxclans.storage.Database.PlayerSettings;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player toggles (§4B): clan chat mode, chat sound, new member sound; stored in {@code player_settings},
 * cached while online. Staff spy is RAM only. Readable from the async chat thread.
 */
public final class PlayerSettingsStore {

    private final Database db;
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();

    public PlayerSettingsStore(Database db) {
        this.db = db;
    }

    /** Loads a joining player's settings (async). */
    public void load(UUID player) {
        db.loadSettings(player).thenAccept(s -> cache.put(player, s));
    }

    public void forget(UUID player) {
        cache.remove(player);
    }

    public PlayerSettings get(UUID player) {
        return cache.getOrDefault(player, PlayerSettings.DEFAULT);
    }

    private void put(UUID player, PlayerSettings s) {
        cache.put(player, s);
        db.saveSettings(player, s);
    }

    /** Toggles clan chat mode; returns the new state. */
    public boolean toggleChatMode(UUID player) {
        PlayerSettings s = get(player);
        PlayerSettings n = new PlayerSettings(s.chatSound(), s.memberSound(), !s.chatMode());
        put(player, n);
        return n.chatMode();
    }

    public void setChatMode(UUID player, boolean on) {
        PlayerSettings s = get(player);
        if (s.chatMode() != on) put(player, new PlayerSettings(s.chatSound(), s.memberSound(), on));
    }

    public boolean toggleChatSound(UUID player) {
        PlayerSettings s = get(player);
        PlayerSettings n = new PlayerSettings(!s.chatSound(), s.memberSound(), s.chatMode());
        put(player, n);
        return n.chatSound();
    }

    public boolean toggleMemberSound(UUID player) {
        PlayerSettings s = get(player);
        PlayerSettings n = new PlayerSettings(s.chatSound(), !s.memberSound(), s.chatMode());
        put(player, n);
        return n.memberSound();
    }

    public boolean isSpy(UUID player) {
        return spies.contains(player);
    }

    /** Toggles spy; returns the new state. */
    public boolean toggleSpy(UUID player) {
        if (spies.remove(player)) return false;
        spies.add(player);
        return true;
    }
}
