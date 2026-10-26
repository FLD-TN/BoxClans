package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.config.ShopConfig;
import com.dex.boxclans.gui.ClanVaultMenu;
import com.dex.boxclans.history.HistoryType;
import com.dex.boxclans.item.Delivery;
import com.dex.boxclans.item.ItemNames;
import com.dex.boxclans.shop.ShopEditor;
import com.dex.boxclans.shop.ShopEntry;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Numbers;
import com.dex.boxclans.vault.VaultItem;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /clanadmin kho give|giveshop|view|remove} (BOXCLANS_SHOP_SPEC.md §5.4): staff put items into a clan storage
 * (rewards of events), look at it, or take items out of it. Every change is kept in the clan history.
 */
public final class VaultCommand implements ClanAdminCommand.SubCommand {

    private static final List<String> ACTIONS = List.of("give", "giveshop", "view", "remove");
    private static final int MAX_AMOUNT = 10_000;

    private final BoxClansPlugin plugin;

    public VaultCommand(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    private void usage(CommandSender to, String label) {
        plugin.messages().send(to, "usage", "usage", "/" + label + " kho give <clan|player> <mã|tên> <danh mục> [số] [-s] | "
                + "giveshop <clan|player> <mã|tên> <id> [số] [-s] | view <mã> | remove <mã> <id ô> [số]");
    }

    @Override
    public void run(CommandSender sender, String label, String[] all) {
        boolean silent = false;
        List<String> list = new ArrayList<>();
        for (String a : all) {
            if (a.equalsIgnoreCase("-s")) silent = true;
            else list.add(a);
        }
        String[] args = list.toArray(new String[0]);
        if (args.length < 2) {
            usage(sender, label);
            return;
        }
        String by = sender instanceof Player p ? p.getName() : "console";
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, label, args, by, silent);
            case "giveshop" -> giveShop(sender, label, args, by, silent);
            case "view" -> view(sender, label, args);
            case "remove" -> remove(sender, label, args, by);
            default -> usage(sender, label);
        }
    }

    private int amount(CommandSender sender, String[] args, int index) {
        if (args.length <= index) return 1;
        long n = Numbers.parsePositive(args[index]);
        if (n <= 0 || n > MAX_AMOUNT) {
            plugin.messages().send(sender, "vault-bad-amount", "value", args[index], "max", MAX_AMOUNT);
            return -1;
        }
        return (int) n;
    }

    private void given(CommandSender sender, ClanData clan, ItemStack item, int amount, String by, boolean silent) {
        plugin.manager().history().record(clan.uuid(), HistoryType.VAULT_GIVE, null, by, null, amount, ItemNames.plain(item));
        Object[] ph = {"clan", clan.name().placeholder(), "item", new Text.Mini(ItemNames.mini(item)), "amount", amount};
        plugin.messages().send(sender, "vault-given", ph);
        if (!silent) plugin.actions().toClan(clan, "vault-given-clan", ph);
    }

    /** {@code give <clan|player> <mã|tên> <danh mục> [số]}: the item in hand, as an ITEM stack. */
    private void give(CommandSender sender, String label, String[] args, String by, boolean silent) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length < 5) {
            usage(sender, label);
            return;
        }
        ClanData clan = ClanTarget.resolve(plugin, sender, args[2], args[3]);
        if (clan == null) return;
        ShopConfig.Category c = plugin.shopConfig().category(args[4]);
        if (c == null || c.icons()) {
            plugin.messages().send(sender, "shop-category-unknown", "category", args[4], "categories",
                    String.join(", ", plugin.shopConfig().itemCategories().stream().map(ShopConfig.Category::id).toList()));
            return;
        }
        int amount = amount(sender, args, 5);
        if (amount < 0) return;
        ItemStack held = ShopEditor.held(p);
        if (held == null) {
            plugin.messages().send(sender, "shop-edit-no-item");
            return;
        }
        plugin.vault().add(clan.uuid(), c.id(), Delivery.ITEM, plugin.itemCodec().encode(held), List.of(), amount);
        given(sender, clan, held, amount, by, silent);
    }

    /** {@code giveshop <clan|player> <mã|tên> <id> [số]}: a copy of a shop entry, commands included. */
    private void giveShop(CommandSender sender, String label, String[] args, String by, boolean silent) {
        if (args.length < 5) {
            usage(sender, label);
            return;
        }
        ClanData clan = ClanTarget.resolve(plugin, sender, args[2], args[3]);
        if (clan == null) return;
        long id = Numbers.parsePositive(args[4].startsWith("#") ? args[4].substring(1) : args[4]);
        ShopEntry e = id <= 0 || id > Integer.MAX_VALUE ? null : plugin.shop().entry((int) id);
        if (e == null) {
            plugin.messages().send(sender, "shop-entry-unknown", "id", args[4]);
            return;
        }
        int amount = amount(sender, args, 5);
        if (amount < 0) return;
        ItemStack item = plugin.shopActions().item(e.item());
        plugin.vault().add(clan.uuid(), plugin.shop().categoryOf(e), e.delivery(), e.item(), e.commands(), amount);
        given(sender, clan, item, amount, by, silent);
    }

    private void view(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length < 3) {
            usage(sender, label);
            return;
        }
        ClanData clan = ClanTarget.resolve(plugin, sender, ClanTarget.CLAN, args[2]);
        if (clan != null) new ClanVaultMenu(plugin, p, clan.uuid(), true).open();
    }

    /** {@code remove <mã> <id ô> [số]}: the id is shown in the staff view of the storage. */
    private void remove(CommandSender sender, String label, String[] args, String by) {
        if (args.length < 4) {
            usage(sender, label);
            return;
        }
        ClanData clan = ClanTarget.resolve(plugin, sender, ClanTarget.CLAN, args[2]);
        if (clan == null) return;
        long id = Numbers.parsePositive(args[3]);
        VaultItem v = id <= 0 ? null : plugin.vault().item(clan.uuid(), id);
        if (v == null) {
            plugin.messages().send(sender, "vault-missing");
            return;
        }
        int amount = args.length > 4 ? amount(sender, args, 4) : v.amount();
        if (amount < 0) return;
        amount = Math.min(amount, v.amount());
        ItemStack item = plugin.shopActions().item(v.item());
        plugin.vault().take(clan.uuid(), v.id(), amount);
        plugin.manager().history().record(clan.uuid(), HistoryType.VAULT_REMOVE, null, by, null, amount,
                item == null ? "?" : ItemNames.plain(item));
        plugin.messages().send(sender, "vault-removed", "clan", clan.name().placeholder(), "amount", amount,
                "item", new Text.Mini(item == null ? "?" : ItemNames.mini(item)));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) return KeyArgs.filter(ACTIONS, args[1]);
        String action = args[1].toLowerCase(Locale.ROOT);
        boolean target = action.equals("give") || action.equals("giveshop");
        if (args.length == 3) {
            return target ? KeyArgs.filter(ClanTarget.KINDS, args[2]) : KeyArgs.filter(KeyArgs.keys(plugin), args[2]);
        }
        if (target && args.length == 4) return ClanTarget.complete(plugin, args[2], args[3]);
        if (target && args.length == 5) {
            List<String> out = new ArrayList<>();
            if (action.equals("give")) {
                for (ShopConfig.Category c : plugin.shopConfig().itemCategories()) out.add(c.id());
            } else {
                for (ShopEntry e : plugin.shop().entries(null, true)) out.add(String.valueOf(e.id()));
            }
            return KeyArgs.filter(out, args[4]);
        }
        if (target && args.length >= 6) return KeyArgs.filter(List.of("1", "-s"), args[args.length - 1]);
        return List.of();
    }
}
