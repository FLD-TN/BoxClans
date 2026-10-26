package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.config.Icons;
import com.dex.boxclans.config.ShopConfig;
import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.hook.ItemsAdderHook;
import com.dex.boxclans.shop.ShopActions;
import com.dex.boxclans.shop.ShopEntry;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Numbers;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code menus/clan-shop.yml}: the clan shop (BOXCLANS_SHOP_SPEC.md §4). Tabs: "Tất cả" then the {@code shop.yml}
 * categories; the icon category lists the shield icons of {@code icons.yml}, the others the entries added in game.
 * Only the Minh Chủ and the Phó Bang buy; staff with {@code boxclans.admin.shop} get an edit mode (§4.6).
 */
public final class ClanShopMenu extends PagedMenu {

    public static final String EDIT_PERMISSION = "boxclans.admin.shop";

    /** One slot of the content: an icon of icons.yml or an entry. */
    private record Offer(Icons.Icon icon, ShopEntry entry) {}

    private String tab;
    private boolean edit;

    public ClanShopMenu(BoxClansPlugin plugin, Player viewer, String tab) {
        super(plugin, viewer, "clan-shop");
        this.tab = tab == null ? ShopConfig.ALL : tab;
    }

    public ClanShopMenu(BoxClansPlugin plugin, Player viewer) {
        this(plugin, viewer, ShopConfig.ALL);
    }

    private ClanData clan() {
        return plugin.manager().of(viewer.getUniqueId());
    }

    @Override
    public void open() {
        if (clan() == null && !edit) {
            plugin.guis().openList(viewer);
            return;
        }
        super.open();
    }

    /** Opens in edit mode (staff), also without a clan. */
    public ClanShopMenu editing(boolean on) {
        this.edit = on && viewer.hasPermission(EDIT_PERMISSION);
        return this;
    }

    private static Text.Mini mini(String legacy) {
        return new Text.Mini(Text.legacyToMini(legacy));
    }

    private ShopConfig config() {
        return plugin.shopConfig();
    }

    /** The menu as it is now, to come back to after a confirmation screen or the entry editor. */
    private Runnable back() {
        String t = tab;
        boolean e = edit;
        int pg = page;
        return () -> {
            ClanShopMenu m = new ClanShopMenu(plugin, viewer, t).editing(e);
            m.page = pg;
            m.open();
        };
    }

    @Override
    protected void draw() {
        ClanData clan = clan();
        if (clan == null && !edit) return;
        fillEmpty();
        drawTabs();
        long balance = clan == null ? 0 : clan.minhHuy();
        placeStatic('M', null, "balance", Numbers.grouped(balance),
                "clan", clan == null ? "" : clan.name().placeholder());

        List<Offer> offers = offers();
        List<Integer> slots = content();
        List<Offer> shown = pageOf(offers);
        for (int i = 0; i < shown.size() && i < slots.size(); i++) {
            Offer o = shown.get(i);
            if (o.icon() != null) {
                set(slots.get(i), iconItem(clan, o.icon()), click -> {
                    if (edit) return;
                    plugin.shopActions().askBuyIcon(viewer, o.icon().id(), iconItem(clan, o.icon()), back());
                });
            } else {
                set(slots.get(i), entryItem(clan, o.entry()), click -> {
                    if (edit) new ShopEntryMenu(plugin, viewer, o.entry().id(), back()).open();
                    else plugin.shopActions().askBuy(viewer, o.entry().id(), back());
                });
            }
        }
        // edit mode, in an item category: an empty slot adds the item on the cursor (§4.6)
        ShopConfig.Category category = config().category(tab);
        if (edit && category != null && !category.icons()) {
            ItemStack add = MenuItems.build(section("add-slot"));
            for (int i = shown.size(); i < slots.size(); i++) {
                if (add != null) set(slots.get(i), add, click -> plugin.shopEditor().addFromCursor(viewer, tab, back()));
            }
        }
        paging('P', 'N', offers.size());
        placeStatic('B', click -> {
            if (clan() != null) plugin.guis().openMain(viewer);
            else viewer.closeInventory();
        });
        if (viewer.hasPermission(EDIT_PERMISSION)) {
            ItemStack toggle = MenuItems.build(itemSection('E'), "state", mini(text(edit ? "edit-on" : "edit-off")));
            place('E', glint(toggle, edit), click -> {
                edit = !edit;
                page = 0;
                refresh();
            });
        }
    }

    private void drawTabs() {
        List<String> tabs = new ArrayList<>();
        tabs.add(ShopConfig.ALL);
        for (ShopConfig.Category c : config().categories()) tabs.add(c.id());
        List<Integer> slots = slots('T');
        for (int i = 0; i < slots.size() && i < tabs.size(); i++) {
            String id = tabs.get(i);
            ShopConfig.Category c = config().category(id);
            ConfigurationSection s = section("tab-item");
            Material m = c == null ? MenuItems.material(YamlFiles.string(cfg, "tab-all.material", "NETHER_STAR"), Material.NETHER_STAR)
                    : MenuItems.material(c.material(), Material.CHEST);
            String name = c == null ? YamlFiles.string(cfg, "tab-all.name", "Tất cả") : c.name();
            ItemStack item = MenuItems.build(m, s, null, "tab", mini(name),
                    "state", mini(text(id.equals(tab) ? "tab-selected" : "tab-other")));
            set(slots.get(i), glint(item, id.equals(tab)), click -> {
                tab = id;
                page = 0;
                refresh();
            });
        }
        drawTabMarkers(tabs.size(), tabs.indexOf(tab), i -> {
            tab = tabs.get(i);
            page = 0;
            refresh();
        });
    }

    private static ItemStack glint(ItemStack item, boolean on) {
        if (item == null || !on) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** What the tab shows: icons for sale first (in "Tất cả" and the icon tab), then the entries. */
    private List<Offer> offers() {
        List<Offer> out = new ArrayList<>();
        ShopConfig.Category c = config().category(tab);
        boolean all = c == null;
        if (all || c.icons()) {
            for (Icons.Icon icon : plugin.icons().all()) if (icon.forSale()) out.add(new Offer(icon, null));
        }
        if (all || !c.icons()) {
            for (ShopEntry e : plugin.shop().entries(all ? null : c.id(), edit)) out.add(new Offer(null, e));
        }
        return out;
    }

    // ------------------------------------------------------------------ items

    private void appendLore(ItemStack item, List<String> lines, Object... ph) {
        if (item == null || lines.isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.addAll(MenuItems.lore(lines, ph));
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private ItemStack entryItem(ClanData clan, ShopEntry e) {
        ItemStack item = plugin.shopActions().item(e.item());
        if (item == null) item = MenuItems.stack(Material.BARRIER, 1);
        ConfigurationSection s = section("entry-item");
        int left = clan == null ? Integer.MAX_VALUE : plugin.shop().left(clan.uuid(), e);
        String state;
        if (clan == null || !ShopActions.manages(clan, viewer.getUniqueId())) state = text("state-member");
        else if (left <= 0) state = text("state-limit");
        else if (clan.minhHuy() < e.price()) state = text("state-poor");
        else state = text("state-buy");
        Object[] ph = {"price", Numbers.grouped(e.price()), "limit", e.weeklyLimit(),
                "bought", clan == null ? 0 : plugin.shop().bought(clan.uuid(), e.id()),
                "state", mini(state), "id", e.id(), "mode", mini(text("mode-" + e.delivery().name().toLowerCase(java.util.Locale.ROOT))),
                "commands", e.commands().size(), "hidden", mini(text(e.hidden() ? "hidden-yes" : "hidden-no")),
                "category", mini(categoryName(plugin.shop().categoryOf(e)))};
        if (s != null) {
            appendLore(item, s.getStringList("lore"), ph);
            if (e.weeklyLimit() > 0) appendLore(item, s.getStringList("lore-limit"), ph);
            appendLore(item, s.getStringList(edit ? "lore-edit" : "lore-state"), ph);
        }
        return item;
    }

    private String categoryName(String id) {
        ShopConfig.Category c = config().category(id);
        return c == null ? id : c.name();
    }

    private Text.Mini glyph(String icon) {
        String g = plugin.iconGlyphs().stillRaw(icon);
        return new Text.Mini(g.isEmpty() ? "" : "<white>" + Text.MM.escapeTags(g) + "</white> ");
    }

    private ItemStack iconItem(ClanData clan, Icons.Icon icon) {
        ConfigurationSection s = section("icon-item");
        boolean owned = clan != null && clan.ownedIcons().contains(icon.id());
        Object price = icon.price() <= 0 ? mini(text("price-free")) : mini(Text.fill(text("price-format"), "amount",
                Numbers.grouped(icon.price())));
        String state;
        if (owned) state = text("icon-state-owned");
        else if (clan == null || !ShopActions.manages(clan, viewer.getUniqueId())) state = text("state-member");
        else if (!icon.permission().isEmpty() && !viewer.hasPermission(icon.permission())) state = text("icon-state-locked");
        else if (icon.price() <= 0) state = text("icon-state-free");
        else if (clan.minhHuy() < icon.price()) state = text("state-poor");
        else state = text("state-buy");
        Object[] ph = {"display", mini(icon.display()), "icon_static", glyph(icon.id()),
                "clan", clan == null ? "" : clan.name().placeholder(), "price", price,
                "permission", icon.permission(), "state", new Text.Mini(Text.fill(Text.legacyToMini(state),
                "permission", icon.permission()))};
        ItemsAdderHook ia = plugin.itemsAdder();
        ItemStack base = ia == null ? null : ia.item(icon.menuItem());
        if (base == null) base = MenuItems.stack(MenuItems.material(icon.fallbackMaterial(), Material.SHIELD), 1);
        return MenuItems.decorate(base, s, null, ph);
    }
}
