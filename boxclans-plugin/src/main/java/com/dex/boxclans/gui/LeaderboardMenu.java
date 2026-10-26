package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** {@code menus/leaderboard.yml}: clans by rank (§8.4), icon = flag. */
public final class LeaderboardMenu extends PagedMenu {

    public LeaderboardMenu(BoxClansPlugin plugin, Player viewer) {
        super(plugin, viewer, "leaderboard");
    }

    @Override
    protected void draw() {
        fillEmpty();
        List<ClanSnapshot> ranked = plugin.index().ranked();
        List<ClanSnapshot> shown = pageOf(ranked);
        List<Integer> slots = content();
        int first = page * Math.max(1, slots.size());
        for (int i = 0; i < shown.size(); i++) {
            ClanSnapshot c = shown.get(i);
            Object[] ph = join(clanPlaceholders(c), new Object[]{"rank", first + i + 1});
            set(slots.get(i), clanIcon(c, "clan-item", ph),
                    click -> plugin.guis().openPreview(viewer, c, () -> plugin.guis().openLeaderboard(viewer)));
        }
        ItemStack empty = MenuItems.build(section("empty-slot"));
        for (int i = shown.size(); i < slots.size(); i++) set(slots.get(i), empty, null);
        placeStatic('B', click -> plugin.guis().openMain(viewer));
        paging('[', ']', ranked.size());
    }
}
