package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanIndex;
import com.dex.boxclans.clan.ClanSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** {@code menus/clan-list.yml}: every clan (§8.4), icon = flag, sort by score / size / name. */
public final class ClanListMenu extends PagedMenu {

    enum Sort { SCORE, SIZE, NAME }

    private Sort sort = Sort.SCORE;

    public ClanListMenu(BoxClansPlugin plugin, Player viewer) {
        super(plugin, viewer, "clan-list");
    }

    @Override
    protected void draw() {
        fillEmpty();
        List<ClanSnapshot> clans = new ArrayList<>(plugin.index().all());
        clans.sort(switch (sort) {
            case SCORE -> ClanIndex.RANKING;
            case SIZE -> Comparator.comparingInt(ClanSnapshot::size).reversed().thenComparing(ClanIndex.RANKING);
            case NAME -> Comparator.comparing(ClanSnapshot::plainName, String.CASE_INSENSITIVE_ORDER);
        });
        List<ClanSnapshot> shown = pageOf(clans);
        List<Integer> slots = content();
        for (int i = 0; i < shown.size(); i++) {
            ClanSnapshot c = shown.get(i);
            set(slots.get(i), clanIcon(c, "clan-item", clanPlaceholders(c)),
                    click -> plugin.guis().openPreview(viewer, c, () -> plugin.guis().openList(viewer)));
        }
        ItemStack empty = MenuItems.build(section("empty-slot"));
        for (int i = shown.size(); i < slots.size(); i++) set(slots.get(i), empty, null);

        placeStatic('L', click -> plugin.guis().openLeaderboard(viewer));
        placeStatic('B', click -> plugin.guis().openInvites(viewer),
                "count", plugin.manager().invites().forPlayer(viewer.getUniqueId()).size());
        if (plugin.manager().of(viewer.getUniqueId()) != null) {
            place('A', MenuItems.build(section("your-clan-button")), click -> plugin.guis().openMain(viewer));
        } else {
            placeStatic('A', click -> plugin.actions().startCreate(viewer, null));
        }
        placeStatic('S', click -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            refresh();
        }, choiceLines("sort", List.of("score", "size", "name"), lower(sort)));
        paging('P', 'N', clans.size());
    }
}
