package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanIndex;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.clan.InviteManager;
import com.dex.boxclans.util.Durations;
import com.dex.boxclans.util.Names;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** {@code menus/invites.yml}: invites received (§8.4); left-click accepts, right-click declines. */
public final class InvitesMenu extends PagedMenu {

    enum Sort { SCORE, SIZE, NAME }

    private record Row(InviteManager.Invite invite, ClanSnapshot clan) {}

    private Sort sort = Sort.SCORE;

    public InvitesMenu(BoxClansPlugin plugin, Player viewer) {
        super(plugin, viewer, "invites");
    }

    @Override
    protected void draw() {
        fillEmpty();
        List<Row> rows = new ArrayList<>();
        for (InviteManager.Invite i : plugin.manager().invites().forPlayer(viewer.getUniqueId())) {
            ClanSnapshot c = plugin.index().get(i.clan());
            if (c != null) rows.add(new Row(i, c));
        }
        Comparator<Row> order = switch (sort) {
            case SCORE -> Comparator.comparing(Row::clan, ClanIndex.RANKING);
            case SIZE -> Comparator.comparingInt((Row r) -> r.clan().size()).reversed();
            case NAME -> Comparator.comparing(r -> r.clan().plainName(), String.CASE_INSENSITIVE_ORDER);
        };
        rows.sort(order);
        List<Row> shown = pageOf(rows);
        List<Integer> slots = content();
        long now = System.currentTimeMillis();
        for (int i = 0; i < shown.size(); i++) {
            Row r = shown.get(i);
            Object[] ph = join(clanPlaceholders(r.clan()), new Object[]{
                    "player", Names.of(r.invite().inviter()),
                    "expiry", Durations.format(Math.max(0, (r.invite().expiresAt() - now) / 1000), plugin.messages())});
            set(slots.get(i), clanIcon(r.clan(), "invite-item", ph), click -> answer(r.clan(), click));
        }
        for (int i = shown.size(); i < slots.size(); i++) set(slots.get(i), MenuItems.build(section("filler")), null);
        placeStatic('B', click -> plugin.guis().openList(viewer));
        placeStatic('S', click -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            refresh();
        }, choiceLines("sort", List.of("score", "size", "name"), lower(sort)));
        paging('P', 'N', rows.size());
    }

    private void answer(ClanSnapshot snap, ClickType click) {
        ClanData clan = plugin.manager().clan(snap.uuid());
        if (clan == null) {
            refresh();
            return;
        }
        if (click.isLeftClick()) {
            plugin.actions().accept(viewer, clan);
            if (plugin.manager().of(viewer.getUniqueId()) != null) {
                plugin.guis().openMain(viewer);
                return;
            }
        } else if (click.isRightClick()) {
            plugin.actions().deny(viewer, clan);
        }
        refresh();
    }
}
