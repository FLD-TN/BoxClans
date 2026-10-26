package com.dex.boxclans.gui;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanSnapshot;
import org.bukkit.entity.Player;

/** Entry points that open the menus of §8.4. Main thread. */
public final class Guis {

    private final BoxClansPlugin plugin;

    public Guis(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    /** {@code /clan}: the clan's main menu, or the clan list without a clan. */
    public void openMain(Player p) {
        if (plugin.manager().of(p.getUniqueId()) == null) {
            openList(p);
            return;
        }
        new ClanMenu(plugin, p).open();
    }

    public void openList(Player p) {
        new ClanListMenu(plugin, p).open();
    }

    public void openLeaderboard(Player p) {
        new LeaderboardMenu(plugin, p).open();
    }

    public void openInvites(Player p) {
        new InvitesMenu(plugin, p).open();
    }

    /** Preview of a clan; {@code back} reopens the menu it came from. */
    public void openPreview(Player p, ClanSnapshot clan, Runnable back) {
        new PreviewMenu(plugin, p, clan.uuid(), back).open();
    }

    public void openPreview(Player p, ClanSnapshot clan) {
        openPreview(p, clan, () -> openList(p));
    }

    public void openSettings(Player p) {
        new SettingsMenu(plugin, p).open();
    }

    public void openShop(Player p) {
        new ClanShopMenu(plugin, p).open();
    }

    /** The clan storage of the player's clan. */
    public void openVault(Player p) {
        var clan = plugin.manager().of(p.getUniqueId());
        if (clan == null) {
            openList(p);
            return;
        }
        new ClanVaultMenu(plugin, p, clan.uuid(), false).open();
    }

    /** The clan history (Minh Chủ / Phó Bang). */
    public void openHistory(Player p) {
        var clan = plugin.manager().of(p.getUniqueId());
        if (clan == null) {
            openList(p);
            return;
        }
        new ClanHistoryMenu(plugin, p, clan.uuid(), false).open();
    }

    /** The shield icons the clan can equip (clan settings). */
    public void openIcons(Player p) {
        new ClanIconMenu(plugin, p).open();
    }

    public void openFlag(Player p) {
        new FlagMakerMenu(plugin, p).open();
    }
}
