package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.config.ShopConfig;
import com.dex.boxclans.gui.ClanShopMenu;
import com.dex.boxclans.item.Delivery;
import com.dex.boxclans.item.ItemNames;
import com.dex.boxclans.shop.ShopEditor;
import com.dex.boxclans.shop.ShopEntry;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Numbers;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /clanadmin shop ...} (BOXCLANS_SHOP_SPEC.md §4.6): the same edits as the edit mode of the shop menu. Adding
 * takes the item in hand; everything else works from the console.
 */
public final class ShopCommand implements ClanAdminCommand.SubCommand {

    private static final List<String> ACTIONS = List.of("edit", "add", "addcmd", "delcmd", "price", "limit",
            "category", "mode", "hide", "show", "remove", "list");

    private final BoxClansPlugin plugin;

    public ShopCommand(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    private ShopEditor editor() {
        return plugin.shopEditor();
    }

    private void usage(CommandSender to, String label) {
        plugin.messages().send(to, "usage", "usage", "/" + label + " shop edit | add <danh mục> <giá> [giới hạn tuần] | "
                + "addcmd <id> <lệnh> | delcmd <id> <số> | price|limit <id> <số> | category <id> <danh mục> | "
                + "mode <id> <item|command> | hide|show|remove <id> | list [danh mục]");
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            usage(sender, label);
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "edit" -> {
                if (!(sender instanceof Player p)) {
                    plugin.messages().send(sender, "player-only");
                    return;
                }
                new ClanShopMenu(plugin, p).editing(true).open();
            }
            case "add" -> add(sender, label, args);
            case "list" -> list(sender, args);
            default -> {
                if (!ACTIONS.contains(action) || args.length < 3) {
                    usage(sender, label);
                    return;
                }
                ShopEntry e = entry(sender, args[2]);
                if (e == null) return;
                edit(sender, label, action, e, args);
            }
        }
    }

    private ShopEntry entry(CommandSender sender, String raw) {
        long id = Numbers.parsePositive(raw.startsWith("#") ? raw.substring(1) : raw);
        ShopEntry e = id <= 0 || id > Integer.MAX_VALUE ? null : plugin.shop().entry((int) id);
        if (e == null) plugin.messages().send(sender, "shop-entry-unknown", "id", raw);
        return e;
    }

    private String category(CommandSender sender, String raw) {
        ShopConfig.Category c = plugin.shopConfig().category(raw);
        if (c == null || c.icons()) {
            List<String> ids = new ArrayList<>();
            for (ShopConfig.Category x : plugin.shopConfig().itemCategories()) ids.add(x.id());
            plugin.messages().send(sender, "shop-category-unknown", "category", raw, "categories", String.join(", ", ids));
            return null;
        }
        return c.id();
    }

    /** {@code add <danh mục> <giá> [giới hạn tuần]}: the item in hand. */
    private void add(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length < 4) {
            usage(sender, label);
            return;
        }
        String category = category(sender, args[2]);
        if (category == null) return;
        long price = Numbers.parsePositive(args[3]);
        long limit = args.length > 4 ? (args[4].equals("0") ? 0 : Numbers.parsePositive(args[4])) : 0;
        if (price <= 0 || limit < 0 || limit > 1_000_000) {
            plugin.messages().send(sender, "shop-edit-bad-number", "value", price <= 0 ? args[3] : args[4]);
            return;
        }
        ItemStack held = ShopEditor.held(p);
        ShopEntry e = editor().addFromHand(p, category, price, (int) limit);
        if (e == null) {
            plugin.messages().send(sender, "shop-edit-no-item");
            return;
        }
        plugin.messages().send(sender, "shop-entry-added", "id", e.id(), "item", new Text.Mini(ItemNames.mini(held)),
                "price", Numbers.grouped(price), "category", category);
    }

    private void edit(CommandSender sender, String label, String action, ShopEntry e, String[] args) {
        int id = e.id();
        switch (action) {
            case "addcmd" -> {
                if (args.length < 4) {
                    usage(sender, label);
                    return;
                }
                String error = editor().addCommand(id, String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                if (error != null) {
                    plugin.messages().send(sender, error, "max", ShopEditor.MAX_COMMANDS);
                    return;
                }
            }
            case "delcmd" -> {
                long n = args.length > 3 ? Numbers.parsePositive(args[3]) : 0;
                if (n < 0 || !editor().removeCommand(id, (int) Math.min(n, Integer.MAX_VALUE))) {
                    plugin.messages().send(sender, "shop-edit-no-command", "id", id);
                    return;
                }
            }
            case "price", "limit" -> {
                if (args.length < 4) {
                    usage(sender, label);
                    return;
                }
                long n = action.equals("limit") && args[3].equals("0") ? 0 : Numbers.parsePositive(args[3]);
                if ((action.equals("price") && n <= 0) || n < 0 || (action.equals("limit") && n > 1_000_000)) {
                    plugin.messages().send(sender, "shop-edit-bad-number", "value", args[3]);
                    return;
                }
                plugin.shop().save(action.equals("price") ? e.withPrice(n) : e.withWeeklyLimit((int) n));
            }
            case "category" -> {
                if (args.length < 4) {
                    usage(sender, label);
                    return;
                }
                String category = category(sender, args[3]);
                if (category == null) return;
                plugin.shop().save(e.withCategory(category).withOrder(Integer.MAX_VALUE / 2));
            }
            case "mode" -> {
                if (args.length < 4 || !(args[3].equalsIgnoreCase("item") || args[3].equalsIgnoreCase("command"))) {
                    usage(sender, label);
                    return;
                }
                plugin.shop().save(e.withDelivery(Delivery.of(args[3])));
            }
            case "hide", "show" -> plugin.shop().save(e.withHidden(action.equals("hide")));
            case "remove" -> {
                plugin.shop().remove(id);
                plugin.messages().send(sender, "shop-entry-removed", "id", id);
                return;
            }
            default -> {
                usage(sender, label);
                return;
            }
        }
        plugin.messages().send(sender, "shop-entry-saved", "id", id);
    }

    private void list(CommandSender sender, String[] args) {
        String category = args.length > 2 ? category(sender, args[2]) : null;
        if (args.length > 2 && category == null) return;
        List<ShopEntry> entries = plugin.shop().entries(category, true);
        plugin.messages().send(sender, "shop-list-header", "count", entries.size());
        for (ShopEntry e : entries) {
            ItemStack item = plugin.shopActions().item(e.item());
            plugin.messages().send(sender, "shop-list-entry", "id", e.id(),
                    "item", new Text.Mini(item == null ? "?" : ItemNames.mini(item)),
                    "category", plugin.shop().categoryOf(e), "price", Numbers.grouped(e.price()),
                    "limit", e.weeklyLimit() <= 0 ? "-" : String.valueOf(e.weeklyLimit()),
                    "mode", e.delivery().name().toLowerCase(Locale.ROOT), "commands", e.commands().size(),
                    "hidden", e.hidden() ? plugin.messages().text("shop-list-hidden") : "");
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) return KeyArgs.filter(ACTIONS, args[1]);
        String action = args[1].toLowerCase(Locale.ROOT);
        List<String> categories = new ArrayList<>();
        for (ShopConfig.Category c : plugin.shopConfig().itemCategories()) categories.add(c.id());
        if (args.length == 3) {
            if (action.equals("add") || action.equals("list")) return KeyArgs.filter(categories, args[2]);
            if (action.equals("edit")) return List.of();
            List<String> ids = new ArrayList<>();
            for (ShopEntry e : plugin.shop().entries(null, true)) ids.add(String.valueOf(e.id()));
            return KeyArgs.filter(ids, args[2]);
        }
        if (args.length == 4) {
            return switch (action) {
                case "category" -> KeyArgs.filter(categories, args[3]);
                case "mode" -> KeyArgs.filter(List.of("item", "command"), args[3]);
                case "add" -> KeyArgs.filter(List.of("100", "500", "1000"), args[3]);
                default -> List.of();
            };
        }
        return List.of();
    }
}
