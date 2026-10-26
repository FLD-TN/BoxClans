package com.dex.boxclans.vault;

import com.dex.boxclans.item.Delivery;
import com.dex.boxclans.storage.Database;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

/**
 * The clan storages (BOXCLANS_SHOP_SPEC.md §5), all in memory (small) and written through the database queue. Only
 * the shop and staff put items in; nothing a player holds can be deposited. Main thread only.
 */
public final class VaultService {

    private final Database db;
    private final LongSupplier clock;
    private final Map<UUID, List<VaultItem>> byClan = new HashMap<>();
    private long nextId = 1;

    public VaultService(Database db, LongSupplier clock) {
        this.db = db;
        this.clock = clock;
    }

    /** Start-up. */
    public void load() throws SQLException {
        byClan.clear();
        for (Database.VaultRow r : db.loadVault()) {
            List<String> commands = r.commands() == null || r.commands().isEmpty() ? List.of()
                    : Arrays.asList(r.commands().split("\n"));
            byClan.computeIfAbsent(r.clan(), k -> new ArrayList<>()).add(new VaultItem(r.id(), r.clan(), r.category(),
                    Delivery.of(r.mode()), r.item(), commands, r.amount(), r.createdAt()));
            nextId = Math.max(nextId, r.id() + 1);
        }
    }

    /**
     * The clan's stacks, oldest first.
     *
     * @param category only this category ({@code null} = all); {@code categoryOf} maps a stored category to the one
     *                 shown (a deleted category shows as {@code khac})
     */
    public List<VaultItem> items(UUID clan, String category, UnaryOperator<String> categoryOf) {
        List<VaultItem> out = new ArrayList<>();
        for (VaultItem v : byClan.getOrDefault(clan, List.of())) {
            if (category == null || categoryOf.apply(v.category()).equals(category)) out.add(v);
        }
        return out;
    }

    public VaultItem item(UUID clan, long id) {
        for (VaultItem v : byClan.getOrDefault(clan, List.of())) if (v.id() == id) return v;
        return null;
    }

    /** Adds {@code amount} (≥ 1), merged into an identical stack when there is one. @return the stack */
    public VaultItem add(UUID clan, String category, Delivery delivery, byte[] item, List<String> commands, int amount) {
        List<VaultItem> list = byClan.computeIfAbsent(clan, k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            VaultItem v = list.get(i);
            if (!v.sameAs(category, delivery, item, commands)) continue;
            VaultItem merged = v.withAmount((int) Math.min(Integer.MAX_VALUE, (long) v.amount() + amount));
            list.set(i, merged);
            save(merged);
            return merged;
        }
        VaultItem created = new VaultItem(nextId++, clan, category, delivery, item, commands, amount, clock.getAsLong());
        list.add(created);
        save(created);
        return created;
    }

    /** Takes {@code amount} from a stack. @return false when the stack is missing or holds less */
    public boolean take(UUID clan, long id, int amount) {
        List<VaultItem> list = byClan.get(clan);
        if (list == null || amount <= 0) return false;
        for (int i = 0; i < list.size(); i++) {
            VaultItem v = list.get(i);
            if (v.id() != id) continue;
            if (v.amount() < amount) return false;
            if (v.amount() == amount) {
                list.remove(i);
                db.deleteVaultItem(id);
            } else {
                VaultItem left = v.withAmount(v.amount() - amount);
                list.set(i, left);
                save(left);
            }
            return true;
        }
        return false;
    }

    /** Number of items (sum of the amounts). */
    public int count(UUID clan) {
        int n = 0;
        for (VaultItem v : byClan.getOrDefault(clan, List.of())) n += v.amount();
        return n;
    }

    /** Drops a disbanded clan's storage from memory (the rows go with the clan). */
    public void forget(UUID clan) {
        byClan.remove(clan);
    }

    private void save(VaultItem v) {
        db.saveVaultItem(new Database.VaultRow(v.id(), v.clan(), v.category(), v.delivery().name(), v.item(),
                String.join("\n", v.commands()), v.amount(), v.createdAt()));
    }
}
