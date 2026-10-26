package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.config.YamlFiles;
import com.dex.boxclans.util.Names;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code menus/clan-preview.yml}: another clan (§8.4). {@code J} joins when invited; otherwise it says "invite
 * only", "already in a clan" or "clan full".
 */
public final class PreviewMenu extends PagedMenu {

    enum Sort { ROLE, NAME, ONLINE, SCORE }

    private final UUID clanId;
    private final Runnable back;
    private Sort sort = Sort.ROLE;

    public PreviewMenu(BoxClansPlugin plugin, Player viewer, UUID clanId, Runnable back) {
        super(plugin, viewer, "clan-preview");
        this.clanId = clanId;
        this.back = back;
    }

    private ClanSnapshot clan() {
        return plugin.index().get(clanId);
    }

    @Override
    protected Object[] titlePlaceholders() {
        ClanSnapshot c = clan();
        return new Object[]{"clan", c == null ? "" : c.clanName().placeholder()};
    }

    @Override
    protected void draw() {
        ClanSnapshot c = clan();
        fillEmpty();
        placeStatic('B', click -> back.run());
        if (c == null) return;
        place('I', clanIcon(c, "items.I", clanPlaceholders(c)), null);
        place('J', joinItem(c), click -> join(c));
        placeStatic('S', click -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            refresh();
        }, choiceLines("sort", List.of("role", "name", "online", "score"), lower(sort)));

        Map<UUID, ClanSnapshot.MemberInfo> info = c.memberInfo();
        List<UUID> members = new ArrayList<>(c.members());
        Comparator<UUID> byRole = Comparator.comparingInt(u -> info.get(u).role().ordinal());
        Comparator<UUID> byName = Comparator.comparing(u -> Names.of(u).toLowerCase());
        members.sort(switch (sort) {
            case ROLE -> byRole.thenComparing(byName);
            case NAME -> byName;
            case ONLINE -> Comparator.<UUID, Boolean>comparing(u -> Bukkit.getPlayer(u) == null).thenComparing(byRole).thenComparing(byName);
            case SCORE -> Comparator.<UUID>comparingLong(u -> -info.get(u).contribution()).thenComparing(byName);
        });
        List<UUID> shown = pageOf(members);
        List<Integer> slots = content();
        ConfigurationSection s = section("member-item");
        for (int i = 0; i < shown.size(); i++) {
            UUID m = shown.get(i);
            ClanSnapshot.MemberInfo mi = info.get(m);
            String nameKey = m.equals(c.leader()) ? "name-leader" : Bukkit.getPlayer(m) != null ? "name-online" : "name-other";
            Object[] ph = {"player", Names.of(m), "role", plugin.actions().guiRole(mi.role()), "score", mi.contribution()};
            ItemStack item = MenuItems.build(Material.PLAYER_HEAD, s, Bukkit.getOfflinePlayer(m), ph);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(MenuItems.line(YamlFiles.string(s, nameKey, "%player%"), ph));
                item.setItemMeta(meta);
            }
            set(slots.get(i), item, null);
        }
        for (int i = shown.size(); i < slots.size(); i++) set(slots.get(i), MenuItems.build(section("filler")), null);
        paging('P', 'N', members.size());
    }

    private enum JoinState { CAN_JOIN, INVITE_ONLY, ALREADY_IN_CLAN, CLAN_FULL }

    private JoinState state(ClanSnapshot c) {
        if (plugin.index().of(viewer.getUniqueId()) != null) return JoinState.ALREADY_IN_CLAN;
        boolean invited = plugin.manager().invites().has(c.uuid(), viewer.getUniqueId());
        boolean full = c.maxMembers() >= 0 && c.size() >= c.maxMembers();
        if (invited) return full ? JoinState.CLAN_FULL : JoinState.CAN_JOIN;
        return JoinState.INVITE_ONLY;
    }

    private ItemStack joinItem(ClanSnapshot c) {
        ConfigurationSection j = section("items.J");
        JoinState st = state(c);
        boolean can = st == JoinState.CAN_JOIN;
        ItemStack item = MenuItems.stack(MenuItems.material(j == null ? null : j.getString("material"), Material.WRITABLE_BOOK), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && j != null) {
            meta.displayName(MenuItems.line(YamlFiles.string(j, can ? "name-can-join" : "name-cannot-join", ""), clanPlaceholders(c)));
            String loreKey = switch (st) {
                case CAN_JOIN -> "lore-can-join";
                case INVITE_ONLY -> "lore-invite-only";
                case ALREADY_IN_CLAN -> "lore-already-in-clan";
                case CLAN_FULL -> "lore-clan-full";
            };
            meta.lore(MenuItems.lore(j.getStringList(loreKey), clanPlaceholders(c)));
            if (can && YamlFiles.bool(j, "glow-can-join", true)) meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void join(ClanSnapshot c) {
        if (state(c) != JoinState.CAN_JOIN) return;
        ClanData data = plugin.manager().clan(c.uuid());
        if (data == null) return;
        plugin.actions().accept(viewer, data);
        if (plugin.manager().of(viewer.getUniqueId()) != null) plugin.guis().openMain(viewer);
        else refresh();
    }
}
