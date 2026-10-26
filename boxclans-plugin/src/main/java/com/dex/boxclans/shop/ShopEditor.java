package com.dex.boxclans.shop;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.config.Messages;
import com.dex.boxclans.config.ShopConfig;
import com.dex.boxclans.item.Delivery;
import com.dex.boxclans.item.ItemNames;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Staff edits of the clan shop (BOXCLANS_SHOP_SPEC.md §4.6), shared by the edit mode of the menus and
 * {@code /clanadmin shop}. Values typed in chat go through the chat prompt. Main thread only.
 */
public final class ShopEditor {

    /** Longest console command kept on an entry. */
    public static final int MAX_COMMAND = 256;
    /** Most commands on one entry. */
    public static final int MAX_COMMANDS = 10;

    private final BoxClansPlugin plugin;

    public ShopEditor(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private ShopService shop() {
        return plugin.shop();
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    /** The item a staff member points at: on the cursor in a menu, else in the main hand. */
    public static ItemStack held(Player p) {
        ItemStack cursor = p.getItemOnCursor();
        if (!empty(cursor)) return cursor;
        ItemStack hand = p.getInventory() == null ? null : p.getInventory().getItemInMainHand();
        return empty(hand) ? null : hand;
    }

    /** Edit mode: an empty slot was clicked; asks the price of the held item, then adds it to the category. */
    public void addFromCursor(Player p, String category, Runnable back) {
        ItemStack held = held(p);
        if (held == null) {
            msg().send(p, "shop-edit-no-item");
            return;
        }
        byte[] data = plugin.itemCodec().encode(held);
        String name = ItemNames.mini(held);
        plugin.actions().prompt(p, "prompt-shop-price", input -> {
            long price = Numbers.parsePositive(input);
            if (price <= 0) {
                msg().send(p, "shop-edit-bad-number", "value", input);
                return false;
            }
            ShopEntry e = shop().add(category, data, price, 0);
            msg().send(p, "shop-entry-added", "id", e.id(), "item", new Text.Mini(name),
                    "price", Numbers.grouped(price), "category", category);
            back.run();
            return true;
        }, "item", new Text.Mini(name));
    }

    /** {@code /clanadmin shop add}: the item in hand, at once. @return the entry, {@code null} without an item */
    public ShopEntry addFromHand(Player p, String category, long price, int weeklyLimit) {
        ItemStack held = held(p);
        if (held == null) return null;
        return shop().add(category, plugin.itemCodec().encode(held), price, weeklyLimit);
    }

    public void askPrice(Player p, int id, Runnable back) {
        plugin.actions().prompt(p, "prompt-shop-price", input -> {
            long price = Numbers.parsePositive(input);
            ShopEntry e = shop().entry(id);
            if (e == null) return true;
            if (price <= 0) {
                msg().send(p, "shop-edit-bad-number", "value", input);
                return false;
            }
            shop().save(e.withPrice(price));
            msg().send(p, "shop-entry-saved", "id", id);
            back.run();
            return true;
        }, "item", "#" + id);
    }

    public void askLimit(Player p, int id, Runnable back) {
        plugin.actions().prompt(p, "prompt-shop-limit", input -> {
            long limit = input.trim().equals("0") ? 0 : Numbers.parsePositive(input);
            ShopEntry e = shop().entry(id);
            if (e == null) return true;
            if (limit < 0 || limit > 1_000_000) {
                msg().send(p, "shop-edit-bad-number", "value", input);
                return false;
            }
            shop().save(e.withWeeklyLimit((int) limit));
            msg().send(p, "shop-entry-saved", "id", id);
            back.run();
            return true;
        }, "item", "#" + id);
    }

    public void askCommand(Player p, int id, Runnable back) {
        plugin.actions().prompt(p, "prompt-shop-command", input -> {
            ShopEntry e = shop().entry(id);
            if (e == null) return true;
            String error = addCommand(id, input);
            if (error != null) {
                msg().send(p, error, "max", MAX_COMMANDS);
                return false;
            }
            msg().send(p, "shop-entry-saved", "id", id);
            back.run();
            return true;
        }, "item", "#" + id);
    }

    /** Adds a console command; the entry becomes a COMMAND entry. @return an error key, {@code null} when added */
    public String addCommand(int id, String command) {
        ShopEntry e = shop().entry(id);
        if (e == null) return "shop-entry-unknown";
        String c = command == null ? "" : command.trim();
        if (c.startsWith("/")) c = c.substring(1);
        if (c.isEmpty() || c.length() > MAX_COMMAND) return "shop-edit-bad-command";
        if (e.commands().size() >= MAX_COMMANDS) return "shop-edit-too-many-commands";
        List<String> list = new ArrayList<>(e.commands());
        list.add(c);
        shop().save(e.withCommands(list).withDelivery(Delivery.COMMAND));
        return null;
    }

    /** Removes command {@code index} (1-based, 0 = the last one). @return false when there is none */
    public boolean removeCommand(int id, int index) {
        ShopEntry e = shop().entry(id);
        if (e == null || e.commands().isEmpty()) return false;
        int i = index <= 0 ? e.commands().size() - 1 : index - 1;
        if (i >= e.commands().size()) return false;
        List<String> list = new ArrayList<>(e.commands());
        list.remove(i);
        shop().save(e.withCommands(list).withDelivery(list.isEmpty() ? Delivery.ITEM : e.delivery()));
        return true;
    }

    /** Next ({@code +1}) or previous item category. */
    public void cycleCategory(int id, int direction) {
        ShopEntry e = shop().entry(id);
        if (e == null) return;
        List<ShopConfig.Category> cats = plugin.shopConfig().itemCategories();
        int i = 0;
        for (int k = 0; k < cats.size(); k++) if (cats.get(k).id().equals(shop().categoryOf(e))) i = k;
        int next = Math.floorMod(i + Integer.signum(direction), cats.size());
        shop().save(e.withCategory(cats.get(next).id()).withOrder(Integer.MAX_VALUE / 2));
    }

    public void toggleDelivery(int id) {
        ShopEntry e = shop().entry(id);
        if (e == null) return;
        shop().save(e.withDelivery(e.delivery() == Delivery.ITEM ? Delivery.COMMAND : Delivery.ITEM));
    }

    public void toggleHidden(int id) {
        ShopEntry e = shop().entry(id);
        if (e != null) shop().save(e.withHidden(!e.hidden()));
    }

    /** Replaces the entry's item with the held one (kept: price, limit, commands). @return false without an item */
    public boolean replaceItem(Player p, int id) {
        ShopEntry e = shop().entry(id);
        ItemStack held = held(p);
        if (e == null || held == null) return false;
        shop().save(e.withItem(plugin.itemCodec().encode(held)));
        return true;
    }
}
