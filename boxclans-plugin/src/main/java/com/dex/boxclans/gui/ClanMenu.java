package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.clan.InviteManager;
import com.dex.boxclans.clan.RolePermission;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Durations;
import com.dex.boxclans.util.Names;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code menus/clan-detail.yml}: the clan's main menu (§8.4). Views: members (click a head: left = Phó Bang,
 * right = Thành Viên, shift-left = transfer, shift-right = kick, §4A) and pending invites (click to cancel).
 * One page only (1.1.0): the {@code X} slots show the first members in the chosen order; a clan never has more
 * members than slots on this server, and pending invites are capped by {@code invite.max-pending}.
 */
public final class ClanMenu extends PagedMenu {

    enum View { MEMBERS, PENDING }

    enum Sort { ROLE, NAME, ONLINE, SCORE }

    private View view = View.MEMBERS;
    private Sort sort = Sort.ROLE;

    public ClanMenu(BoxClansPlugin plugin, Player viewer) {
        super(plugin, viewer, "clan-detail");
    }

    private ClanData clan() {
        return plugin.manager().of(viewer.getUniqueId());
    }

    @Override
    public void open() {
        if (clan() == null) {
            plugin.guis().openList(viewer);
            return;
        }
        super.open();
    }

    @Override
    protected Object[] titlePlaceholders() {
        ClanData clan = clan();
        return new Object[]{"view", new Text.Mini(Text.legacyToMini(text("view-title-labels." + lower(view)))),
                "clan", clan == null ? "" : clan.name().placeholder()};
    }

    @Override
    protected void draw() {
        ClanData clan = clan();
        if (clan == null) return;
        ClanSnapshot snap = plugin.index().get(clan.uuid());
        fillEmpty();
        placeStatic('L', click -> plugin.guis().openList(viewer));
        place('I', infoItem(snap), null);
        placeStatic('A', click -> plugin.guis().openSettings(viewer));
        placeStatic('H', click -> plugin.guis().openShop(viewer));
        placeStatic('K', click -> plugin.guis().openVault(viewer));
        placeStatic('G', click -> plugin.guis().openHistory(viewer));
        placeStatic('S', click -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            refresh();
        }, choiceLines("sort", List.of("role", "name", "online", "score"), lower(sort)));
        placeStatic('F', click -> {
            view = view == View.MEMBERS ? View.PENDING : View.MEMBERS;
            open();
        }, choiceLines("view", List.of("members", "pending"), lower(view)));
        if (view == View.MEMBERS) drawMembers(clan, snap);
        else drawPending(clan);
    }

    private ItemStack infoItem(ClanSnapshot snap) {
        String unlimited = text("unlimited");
        boolean over = snap.maxMembers() >= 0 && snap.size() >= snap.maxMembers();
        Object[] ph = join(clanPlaceholders(snap), new Object[]{
                "members_color", new Text.Mini(Text.legacyToMini(text(over ? "members-color-full" : "members-color-normal"))),
                "pvp", new Text.Mini(Text.legacyToMini(text(snap.friendlyFire() ? "pvp-on" : "pvp-off"))),
                "unlimited", unlimited});
        return clanIcon(snap, "items.I", ph);
    }

    private void drawMembers(ClanData clan, ClanSnapshot snap) {
        List<UUID> members = new ArrayList<>(snap.members());
        Map<UUID, ClanSnapshot.MemberInfo> info = snap.memberInfo();
        Comparator<UUID> byRole = Comparator.comparingInt(u -> info.get(u).role().ordinal());
        Comparator<UUID> byName = Comparator.comparing(u -> Names.of(u).toLowerCase());
        Comparator<UUID> order = switch (sort) {
            case ROLE -> byRole.thenComparing(byName);
            case NAME -> byName;
            case ONLINE -> Comparator.<UUID, Boolean>comparing(u -> Bukkit.getPlayer(u) == null).thenComparing(byRole).thenComparing(byName);
            case SCORE -> Comparator.<UUID>comparingLong(u -> -info.get(u).contribution()).thenComparing(byName);
        };
        members.sort(order);
        List<UUID> shown = firstPage(members);
        List<Integer> slots = content();
        boolean viewerLeader = clan.leader().equals(viewer.getUniqueId());
        boolean canKick = plugin.manager().can(clan, viewer.getUniqueId(), RolePermission.KICK_MEMBERS);
        for (int i = 0; i < shown.size(); i++) {
            UUID m = shown.get(i);
            ClanSnapshot.MemberInfo mi = info.get(m);
            set(slots.get(i), memberItem(m, mi, viewerLeader, canKick), click -> memberClick(m, click));
        }
        boolean canInvite = plugin.manager().can(clan, viewer.getUniqueId(), RolePermission.INVITE_MEMBERS);
        ItemStack empty = canInvite ? MenuItems.build(section("empty-slot")) : MenuItems.build(section("filler"));
        for (int i = shown.size(); i < slots.size(); i++) {
            set(slots.get(i), empty, canInvite ? click -> plugin.actions().startInvite(viewer) : null);
        }
    }

    private ItemStack memberItem(UUID m, ClanSnapshot.MemberInfo mi, boolean viewerLeader, boolean canKick) {
        boolean online = Bukkit.getPlayer(m) != null;
        boolean self = m.equals(viewer.getUniqueId());
        ConfigurationSection s = section("member-item");
        Object[] ph = {"player", Names.of(m), "role", plugin.actions().guiRole(mi.role()), "score", mi.contribution(),
                "status_color", new Text.Mini(Text.legacyToMini(text(online ? "status-online" : "status-offline"))),
                "you_tag", self ? new Text.Mini(Text.legacyToMini(text("you-tag"))) : "",
                "last_seen", lastSeen(online, mi.lastSeen())};
        ItemStack item = MenuItems.build(Material.PLAYER_HEAD, s, Bukkit.getOfflinePlayer(m), ph);
        List<String> extra = List.of();
        if (!self && viewerLeader) extra = s.getStringList("lore-leader");
        else if (!self && canKick && mi.role() == ClanRole.MEMBER) extra = s.getStringList("lore-kick");
        appendLore(item, extra, ph);
        return item;
    }

    /** {@code %last_seen%}: "Đang online", "3 ngày 4 giờ trước" (two units at most), or unknown. */
    private Text.Mini lastSeen(boolean online, long at) {
        if (online) return new Text.Mini(Text.legacyToMini(text("last-seen-online")));
        if (at <= 0) return new Text.Mini(Text.legacyToMini(text("last-seen-unknown")));
        long seconds = Math.max(0, (System.currentTimeMillis() - at) / 1000);
        return new Text.Mini(Text.fill(Text.legacyToMini(text("last-seen-ago")),
                "time", Durations.format(seconds, plugin.messages())));
    }

    static void appendLore(ItemStack item, List<String> lines, Object... ph) {
        if (lines.isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.addAll(MenuItems.lore(lines, ph));
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private void memberClick(UUID target, ClickType click) {
        if (target.equals(viewer.getUniqueId())) return;
        ClanData clan = clan();
        if (clan == null) return;
        boolean leader = clan.leader().equals(viewer.getUniqueId());
        switch (click) {
            case SHIFT_RIGHT -> plugin.actions().kick(viewer, target, true);
            case SHIFT_LEFT -> {
                if (leader) plugin.actions().setRole(viewer, target, ClanRole.LEADER);
            }
            case LEFT -> {
                if (leader) plugin.actions().setRole(viewer, target, ClanRole.VICE);
            }
            case RIGHT -> {
                if (leader) plugin.actions().setRole(viewer, target, ClanRole.MEMBER);
            }
            default -> {
                return;
            }
        }
        refresh(); // harmless when a confirmation screen replaced this menu
    }

    private void drawPending(ClanData clan) {
        List<InviteManager.Invite> invites = new ArrayList<>(plugin.manager().invites().ofClan(clan.uuid()));
        List<InviteManager.Invite> shown = firstPage(invites);
        List<Integer> slots = content();
        boolean canCancel = plugin.manager().can(clan, viewer.getUniqueId(), RolePermission.INVITE_MEMBERS);
        long now = System.currentTimeMillis();
        ConfigurationSection s = section("pending-item");
        for (int i = 0; i < shown.size(); i++) {
            InviteManager.Invite inv = shown.get(i);
            Object[] ph = {"player", Names.of(inv.target()), "inviter", Names.of(inv.inviter()),
                    "expiry", Durations.format(Math.max(0, (inv.expiresAt() - now) / 1000), plugin.messages())};
            ItemStack item = MenuItems.build(Material.PLAYER_HEAD, s, Bukkit.getOfflinePlayer(inv.target()), ph);
            if (canCancel) appendLore(item, s.getStringList("lore-cancel"), ph);
            set(slots.get(i), item, canCancel ? click -> {
                plugin.actions().uninvite(viewer, inv.target());
                refresh();
            } : null);
        }
        ItemStack filler = MenuItems.build(section("filler"));
        for (int i = shown.size(); i < slots.size(); i++) set(slots.get(i), filler, null);
    }

    /** The items that fit on the {@code X} slots. */
    private <T> List<T> firstPage(List<T> all) {
        return new ArrayList<>(all.subList(0, Math.min(all.size(), content().size())));
    }
}
