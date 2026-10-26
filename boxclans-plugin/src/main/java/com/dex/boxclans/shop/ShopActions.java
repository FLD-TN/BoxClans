package com.dex.boxclans.shop;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.config.Icons;
import com.dex.boxclans.config.Messages;
import com.dex.boxclans.gui.ConfirmMenu;
import com.dex.boxclans.history.HistoryType;
import com.dex.boxclans.hook.ClanBoosts;
import com.dex.boxclans.item.Delivery;
import com.dex.boxclans.item.Inventories;
import com.dex.boxclans.item.ItemNames;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Durations;
import com.dex.boxclans.util.Numbers;
import com.dex.boxclans.vault.VaultItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * What players do in the clan shop and storage (BOXCLANS_SHOP_SPEC.md §3, §4.4, §5.3): buy (always confirmed, and
 * checked again when confirmed), withdraw, use a clan boost bottle. Only the Minh Chủ and the Phó Bang may buy, take
 * out or use; everything is announced to the clan and kept in its history. Main thread only.
 */
public final class ShopActions {

    /** One shop / storage action per player per this many milliseconds (double-click guard). */
    private static final long SPAM_MS = 250;

    private final BoxClansPlugin plugin;
    private final Map<UUID, Long> lastAction = new HashMap<>();

    public ShopActions(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    /** Minh Chủ or Phó Bang. */
    public static boolean manages(ClanData clan, UUID player) {
        if (clan == null) return false;
        ClanRole role = clan.roleOf(player);
        return role == ClanRole.LEADER || role == ClanRole.VICE;
    }

    private boolean tooFast(UUID player) {
        long now = System.currentTimeMillis();
        // 1.4.4: old entries are dropped, the map stays as small as the players clicking right now
        if (lastAction.size() > 64) lastAction.values().removeIf(t -> now - t >= SPAM_MS);
        Long last = lastAction.put(player, now);
        return last != null && now - last < SPAM_MS;
    }

    private void error(Player p, String key, Object... placeholders) {
        msg().send(p, key, placeholders);
        plugin.sounds().play(p, "error");
    }

    private Text.Mini role(ClanData clan, UUID player) {
        ClanRole r = clan.roleOf(player);
        return r == null ? new Text.Mini("") : plugin.actions().role(r);
    }

    /** The decoded item of an entry, {@code null} (and logged) when it cannot be read any more. */
    public ItemStack item(byte[] data) {
        try {
            return plugin.itemCodec().decode(data);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read a stored shop / storage item", ex);
            return null;
        }
    }

    // ------------------------------------------------------------------ shop items (§4.4)

    /** Why the clan cannot buy the entry now ({@code messages.yml} key), {@code null} when it can. */
    private String refuse(Player p, ClanData clan, ShopEntry e, long price) {
        if (clan == null) return "error-not-in-clan";
        if (!manages(clan, p.getUniqueId())) return "shop-not-manager";
        if (e == null || e.hidden()) return "shop-not-for-sale";
        if (e.price() != price) return "shop-price-changed";
        if (plugin.shop().left(clan.uuid(), e) <= 0) return "shop-weekly-limit";
        if (clan.minhHuy() < e.price()) return "shop-not-enough";
        return null;
    }

    private Object[] entryPlaceholders(ClanData clan, ShopEntry e, ItemStack item) {
        int left = plugin.shop().left(clan.uuid(), e);
        return new Object[]{"item", new Text.Mini(ItemNames.mini(item)), "price", Numbers.grouped(e.price()),
                "balance", Numbers.grouped(clan.minhHuy()), "after", Numbers.grouped(clan.minhHuy() - e.price()),
                "limit", e.weeklyLimit(), "left", left == Integer.MAX_VALUE ? "-" : String.valueOf(left)};
    }

    /** A click on an entry: the confirmation screen, or why not. {@code back} reopens the shop afterwards. */
    public void askBuy(Player p, int entryId, Runnable back) {
        ClanData clan = plugin.manager().of(p.getUniqueId());
        ShopEntry e = plugin.shop().entry(entryId);
        String why = refuse(p, clan, e, e == null ? 0 : e.price());
        if (why != null) {
            error(p, why, clan == null || e == null ? new Object[0] : entryPlaceholders(clan, e, item(e.item())));
            return;
        }
        ItemStack shown = item(e.item());
        if (shown == null) {
            error(p, "shop-not-for-sale");
            return;
        }
        long price = e.price();
        new ConfirmMenu.Request(plugin, p, "shop-buy").icon(shown).placeholders(entryPlaceholders(clan, e, shown))
                .onCancel(back).open(() -> {
                    buy(p, entryId, price);
                    back.run();
                });
    }

    /**
     * Buys one entry at {@code price} (what the player confirmed). Everything is checked again: a price changed by
     * staff, the limit, the balance and the role at the moment of the click.
     *
     * @return true when bought
     */
    public boolean buy(Player p, int entryId, long price) {
        ClanData clan = plugin.manager().of(p.getUniqueId());
        ShopEntry e = plugin.shop().entry(entryId);
        String why = refuse(p, clan, e, price);
        ItemStack shown = e == null ? null : item(e.item());
        if (why == null && shown == null) why = "shop-not-for-sale";
        if (why != null) {
            error(p, why, clan == null || e == null ? new Object[0] : entryPlaceholders(clan, e, shown));
            return false;
        }
        String name = ItemNames.plain(shown);
        boolean[] paid = {false};
        // Minh Huy, storage, weekly counter and history are written together or not at all (1.4.4)
        plugin.database().atomically("buy #" + e.id() + " for " + clan.key(), () -> {
            if (!plugin.minhHuy().spend(clan, e.price(), name).success()) return;
            paid[0] = true;
            plugin.vault().add(clan.uuid(), plugin.shop().categoryOf(e), e.delivery(), e.item(), e.commands(), 1);
            plugin.shop().countPurchase(clan.uuid(), e.id());
            plugin.manager().history().record(clan.uuid(), HistoryType.SHOP_BUY, p.getUniqueId(), null, null, e.price(),
                    name);
        });
        if (!paid[0]) {
            error(p, "shop-not-enough", entryPlaceholders(clan, e, shown));
            return false;
        }
        plugin.actions().toClan(clan, "shop-bought-clan", "player", p.getName(), "role", role(clan, p.getUniqueId()),
                "item", new Text.Mini(ItemNames.mini(shown)), "price", Numbers.grouped(e.price()),
                "balance", Numbers.grouped(clan.minhHuy()));
        plugin.sounds().play(p, "shop-bought");
        return true;
    }

    // ------------------------------------------------------------------ icons (§3, §4.4)

    private static String iconPlain(Icons.Icon icon) {
        return Text.plain(Text.parse(Text.legacyToMini(icon.display())));
    }

    private String refuseIcon(Player p, ClanData clan, Icons.Icon icon, long price) {
        if (clan == null) return "error-not-in-clan";
        if (!manages(clan, p.getUniqueId())) return "shop-not-manager";
        if (icon == null || !icon.forSale()) return "shop-not-for-sale";
        if (clan.ownedIcons().contains(icon.id())) return "shop-icon-owned";
        if (icon.price() != price) return "shop-price-changed";
        if (!icon.permission().isEmpty() && !p.hasPermission(icon.permission())) return "icon-locked";
        if (clan.minhHuy() < icon.price()) return "shop-not-enough";
        return null;
    }

    private Object[] iconPlaceholders(ClanData clan, Icons.Icon icon) {
        return new Object[]{"icon", new Text.Mini(Text.legacyToMini(icon.display())),
                "item", new Text.Mini(Text.legacyToMini(icon.display())), "price", Numbers.grouped(icon.price()),
                "balance", Numbers.grouped(clan == null ? 0 : clan.minhHuy()),
                "after", Numbers.grouped(clan == null ? 0 : clan.minhHuy() - icon.price()),
                "permission", icon.permission()};
    }

    public void askBuyIcon(Player p, String iconId, ItemStack shown, Runnable back) {
        ClanData clan = plugin.manager().of(p.getUniqueId());
        Icons.Icon icon = plugin.icons().icon(iconId);
        String why = refuseIcon(p, clan, icon, icon == null ? 0 : icon.price());
        if (why != null) {
            error(p, why, icon == null ? new Object[0] : iconPlaceholders(clan, icon));
            return;
        }
        long price = icon.price();
        new ConfirmMenu.Request(plugin, p, "shop-buy-icon").icon(shown).placeholders(iconPlaceholders(clan, icon))
                .onCancel(back).open(() -> {
                    buyIcon(p, iconId, price);
                    back.run();
                });
    }

    /** Buys (or claims, price 0) an icon; it is owned, not equipped (§3). @return true when bought */
    public boolean buyIcon(Player p, String iconId, long price) {
        ClanData clan = plugin.manager().of(p.getUniqueId());
        Icons.Icon icon = plugin.icons().icon(iconId);
        String why = refuseIcon(p, clan, icon, price);
        if (why != null) {
            error(p, why, icon == null ? new Object[0] : iconPlaceholders(clan, icon));
            return false;
        }
        boolean[] paid = {false};
        plugin.database().atomically("buy icon " + icon.id() + " for " + clan.key(), () -> {
            if (icon.price() > 0 && !plugin.minhHuy().spend(clan, icon.price(), iconPlain(icon)).success()) return;
            paid[0] = true;
            plugin.manager().unlockIcon(clan, icon.id());
            plugin.manager().history().record(clan.uuid(), HistoryType.SHOP_BUY, p.getUniqueId(), null, null,
                    icon.price(), iconPlain(icon));
        });
        if (!paid[0]) {
            error(p, "shop-not-enough", iconPlaceholders(clan, icon));
            return false;
        }
        plugin.actions().toClan(clan, "shop-bought-clan", "player", p.getName(), "role", role(clan, p.getUniqueId()),
                "item", new Text.Mini(Text.legacyToMini(icon.display())), "price", Numbers.grouped(icon.price()),
                "balance", Numbers.grouped(clan.minhHuy()));
        msg().send(p, "shop-icon-bought", iconPlaceholders(clan, icon));
        plugin.sounds().play(p, "shop-bought");
        return true;
    }

    // ------------------------------------------------------------------ storage (§5.3)

    /** Takes one out: the item goes to the player's inventory, or a COMMAND entry runs its commands. */
    public boolean withdraw(Player p, long vaultId) {
        UUID id = p.getUniqueId();
        ClanData clan = plugin.manager().of(id);
        if (clan == null) {
            error(p, "error-not-in-clan");
            return false;
        }
        if (!manages(clan, id)) {
            error(p, "vault-not-manager");
            return false;
        }
        VaultItem v = plugin.vault().item(clan.uuid(), vaultId);
        ItemStack item = v == null ? null : item(v.item());
        if (v == null || item == null) {
            error(p, "vault-missing");
            return false;
        }
        if (v.delivery() == Delivery.ITEM && !Inventories.hasRoom(p, item)) {
            error(p, "vault-inventory-full");
            return false;
        }
        // only a withdrawal that would happen counts for the double-click guard
        if (tooFast(id)) return false;
        String name = ItemNames.plain(item);
        boolean[] taken = {false};
        plugin.database().atomically("withdraw " + vaultId + " from " + clan.key(), () -> {
            if (!plugin.vault().take(clan.uuid(), vaultId, 1)) return;
            taken[0] = true;
            plugin.manager().history().record(clan.uuid(), HistoryType.VAULT_WITHDRAW, id, null, null, 1, name);
        });
        if (!taken[0]) {
            error(p, "vault-missing");
            return false;
        }
        if (v.delivery() == Delivery.ITEM) {
            Inventories.give(p, item);
        } else {
            runCommands(v, p, clan);
        }
        plugin.actions().toClan(clan, "vault-withdrawn-clan", "player", p.getName(), "role", role(clan, id),
                "item", new Text.Mini(ItemNames.mini(item)));
        plugin.sounds().play(p, "vault-withdrawn");
        return true;
    }

    /** Console commands of a COMMAND stack, for one item. A failing command is logged, never refunded (§5.3). */
    private void runCommands(VaultItem v, Player p, ClanData clan) {
        for (String raw : v.commands()) {
            String cmd = raw.replace("{player}", p.getName()).replace("{clan}", clan.key()).replace("{amount}", "1");
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)) {
                    plugin.getLogger().warning("Storage command did not run (unknown command?): " + cmd);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Storage command failed: " + cmd, ex);
            }
        }
    }

    /** The clan bottle data of a stack, {@code null} when it is not a BoxCore clan bottle or BoxCore is missing. */
    public ClanBoosts.Bottle clanBottle(VaultItem v) {
        ClanBoosts boosts = plugin.clanBoosts();
        ItemStack item = boosts == null ? null : item(v.item());
        if (item == null) return null;
        ClanBoosts.Bottle b = boosts.readBottle(item);
        return b != null && b.clan() ? b : null;
    }

    /**
     * Uses one clan boost bottle from the storage for the clan. A replacement or a cut extension asks first
     * ({@code confirmed = false}); the bottle leaves the storage only when the boost changed.
     */
    public boolean use(Player p, long vaultId, boolean confirmed, Runnable back) {
        UUID id = p.getUniqueId();
        ClanData clan = plugin.manager().of(id);
        if (clan == null) {
            error(p, "error-not-in-clan");
            return false;
        }
        if (!manages(clan, id)) {
            error(p, "vault-not-manager");
            return false;
        }
        VaultItem v = plugin.vault().item(clan.uuid(), vaultId);
        if (v == null) {
            error(p, "vault-missing");
            return false;
        }
        ClanBoosts boosts = plugin.clanBoosts();
        ClanBoosts.Bottle b = boosts == null ? null : clanBottle(v);
        if (b == null) {
            error(p, "vault-boost-unavailable");
            return false;
        }
        ItemStack item = item(v.item());
        if (!confirmed && tooFast(id)) return false;
        ClanBoosts.Result r = boosts.start(clan.uuid(), b.type(), b.tenths(), b.seconds(), confirmed, p.getName());
        Object[] ph = {"item", new Text.Mini(ItemNames.mini(item)), "type", b.type(),
                "multiplier", tenths(b.tenths()), "old", tenths(r.oldTenths()),
                "lost", Durations.format(r.lostSeconds(), msg()),
                "remaining", Durations.format(Math.max(0, (r.oldExpiresAt() - System.currentTimeMillis()) / 1000), msg())};
        switch (r.status()) {
            case CONFIRM_REPLACE, CONFIRM_CAPPED -> {
                String action = r.status() == ClanBoosts.Status.CONFIRM_REPLACE ? "vault-boost-replace" : "vault-boost-capped";
                new ConfirmMenu.Request(plugin, p, action).icon(item).placeholders(ph).onCancel(back).open(() -> {
                    use(p, vaultId, true, back);
                    back.run();
                });
                return false;
            }
            case LOWER -> {
                error(p, "vault-boost-lower", ph);
                return false;
            }
            case FULL -> {
                error(p, "vault-boost-full", ph);
                return false;
            }
            case INVALID -> {
                error(p, "vault-boost-invalid", ph);
                return false;
            }
            case UNAVAILABLE -> {
                error(p, "vault-boost-unavailable", ph);
                return false;
            }
            default -> {
                // STARTED / EXTENDED / REPLACED: BoxCore told the clan about the boost
            }
        }
        String name = ItemNames.plain(item);
        plugin.database().atomically("use " + vaultId + " from " + clan.key(), () -> {
            if (!plugin.vault().take(clan.uuid(), vaultId, 1)) {
                // cannot happen on the main thread (checked above); logged so it would never go unnoticed
                plugin.getLogger().warning("Clan boost started but the bottle was already gone from the storage of "
                        + clan.key());
            }
            plugin.manager().history().record(clan.uuid(), HistoryType.VAULT_USE, id, null, null, 1, name);
        });
        Object[] all = new Object[ph.length + 4];
        all[0] = "player";
        all[1] = p.getName();
        all[2] = "role";
        all[3] = role(clan, id);
        System.arraycopy(ph, 0, all, 4, ph.length);
        plugin.actions().toClan(clan, "vault-used-clan", all);
        plugin.sounds().play(p, "vault-withdrawn");
        return true;
    }

    /** {@code 20} → {@code 2}, {@code 15} → {@code 1.5}. */
    public static String tenths(int tenths) {
        return tenths % 10 == 0 ? String.valueOf(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
    }
}
