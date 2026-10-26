package com.dex.boxclans.listener;

import com.dex.boxclans.api.event.ClanDisbandEvent;
import com.dex.boxclans.shop.ShopService;
import com.dex.boxclans.vault.VaultService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** A disbanded clan loses its storage and purchase counters (the database rows are deleted with the clan). */
public final class ShopListener implements Listener {

    private final ShopService shop;
    private final VaultService vault;

    public ShopListener(ShopService shop, VaultService vault) {
        this.shop = shop;
        this.vault = vault;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisband(ClanDisbandEvent event) {
        vault.forget(event.clan().uuid());
        shop.forget(event.clan().uuid());
    }
}
