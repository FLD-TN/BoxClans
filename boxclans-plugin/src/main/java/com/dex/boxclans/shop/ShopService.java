package com.dex.boxclans.shop;

import com.dex.boxclans.config.ShopConfig;
import com.dex.boxclans.item.Delivery;
import com.dex.boxclans.storage.Database;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The entries of the clan shop and the weekly purchase counters (BOXCLANS_SHOP_SPEC.md §4.2, §4.5), kept in memory
 * and written through the database queue. Main thread only.
 */
public final class ShopService {

    /** Purchase counters of older weeks are deleted after this many weeks. */
    private static final int KEEP_WEEKS = 5;

    private final Database db;
    private final Supplier<ShopConfig> config;
    private final LongSupplier clock;
    private final Map<Integer, ShopEntry> entries = new LinkedHashMap<>();
    /** {@code clan|entry} → purchases in {@link #week}. */
    private final Map<String, Integer> purchases = new HashMap<>();
    private long week;

    public ShopService(Database db, Supplier<ShopConfig> config, LongSupplier clock) {
        this.db = db;
        this.config = config;
        this.clock = clock;
    }

    /** Start-up: entries and this week's counters. */
    public void load() throws SQLException {
        entries.clear();
        for (Database.ShopRow r : db.loadShopEntries()) {
            List<String> commands = r.commands() == null || r.commands().isEmpty() ? List.of()
                    : Arrays.asList(r.commands().split("\n"));
            entries.put(r.id(), new ShopEntry(r.id(), r.category(), r.item(), Delivery.of(r.mode()), commands,
                    r.price(), r.weeklyLimit(), r.order(), r.hidden()));
        }
        week = config.get().weekStart(clock.getAsLong());
        purchases.clear();
        purchases.putAll(db.loadPurchases(week, week - KEEP_WEEKS * 7L * 86_400_000L));
    }

    // ------------------------------------------------------------------ entries

    public ShopEntry entry(int id) {
        return entries.get(id);
    }

    /** The category an entry is shown in (a deleted category falls back to {@code khac}). */
    public String categoryOf(ShopEntry e) {
        return config.get().itemCategory(e.category());
    }

    /**
     * Entries of a category ({@code null} = every category, in {@code shop.yml} order), by {@code order} then id.
     *
     * @param hidden include hidden entries (staff in edit mode)
     */
    public List<ShopEntry> entries(String category, boolean hidden) {
        List<ShopEntry> out = new ArrayList<>();
        ShopConfig cfg = config.get();
        List<String> order = new ArrayList<>();
        for (ShopConfig.Category c : cfg.itemCategories()) order.add(c.id());
        for (ShopEntry e : entries.values()) {
            if (!hidden && e.hidden()) continue;
            if (category != null && !categoryOf(e).equals(category)) continue;
            out.add(e);
        }
        out.sort(Comparator.<ShopEntry>comparingInt(e -> order.indexOf(categoryOf(e)))
                .thenComparingInt(ShopEntry::order).thenComparingInt(ShopEntry::id));
        return out;
    }

    /** A new visible entry at the end of its category. */
    public ShopEntry add(String category, byte[] item, long price, int weeklyLimit) {
        int id = 1;
        int order = 0;
        for (ShopEntry e : entries.values()) {
            id = Math.max(id, e.id() + 1);
            if (categoryOf(e).equals(category)) order = Math.max(order, e.order() + 1);
        }
        ShopEntry e = new ShopEntry(id, category, item, Delivery.ITEM, List.of(), price, Math.max(0, weeklyLimit),
                order, false);
        save(e);
        return e;
    }

    /** Stores a changed entry (same id). */
    public ShopEntry save(ShopEntry e) {
        entries.put(e.id(), e);
        db.saveShopEntry(new Database.ShopRow(e.id(), e.category(), e.item(), e.delivery().name(),
                String.join("\n", e.commands()), e.price(), e.weeklyLimit(), e.order(), e.hidden()));
        return e;
    }

    public boolean remove(int id) {
        if (entries.remove(id) == null) return false;
        db.deleteShopEntry(id);
        purchases.keySet().removeIf(k -> k.endsWith("|" + id));
        return true;
    }

    /** Swaps an entry with its neighbour in its category ({@code -1} = earlier). @return false at an end */
    public boolean move(int id, int direction) {
        ShopEntry e = entries.get(id);
        if (e == null) return false;
        List<ShopEntry> list = entries(categoryOf(e), true);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id() != id) continue;
            int j = i + Integer.signum(direction);
            if (j < 0 || j >= list.size()) return false;
            ShopEntry other = list.get(j);
            // renumber the whole category so equal orders never block a move
            for (int k = 0; k < list.size(); k++) {
                ShopEntry x = list.get(k);
                int order = k == i ? j : k == j ? i : k;
                if (x.order() != order || x.id() == id || x.id() == other.id()) save(x.withOrder(order));
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ weekly limit (§4.5)

    private void rollWeek() {
        long now = config.get().weekStart(clock.getAsLong());
        if (now != week) {
            week = now;
            purchases.clear();
        }
    }

    /** Purchases of the entry by the clan this week. */
    public int bought(UUID clan, int entry) {
        rollWeek();
        return purchases.getOrDefault(clan + "|" + entry, 0);
    }

    /** Purchases left this week, {@link Integer#MAX_VALUE} without a limit. */
    public int left(UUID clan, ShopEntry e) {
        if (e.weeklyLimit() <= 0) return Integer.MAX_VALUE;
        return Math.max(0, e.weeklyLimit() - bought(clan, e.id()));
    }

    public void countPurchase(UUID clan, int entry) {
        rollWeek();
        int n = purchases.merge(clan + "|" + entry, 1, Integer::sum);
        db.savePurchases(clan, entry, week, n);
    }

    /** Drops the counters of a disbanded clan (the database rows go with the clan). */
    public void forget(UUID clan) {
        purchases.keySet().removeIf(k -> k.startsWith(clan + "|"));
    }
}
