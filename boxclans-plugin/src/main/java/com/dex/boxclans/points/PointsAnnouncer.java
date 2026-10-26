package com.dex.boxclans.points;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Names;
import org.bukkit.entity.Player;

import java.util.UUID;

/** {@link PointsService.Announcer} with {@code messages.yml}: {@code points-gained}, {@code points-lost}, {@code points-clan-cap}. */
public final class PointsAnnouncer implements PointsService.Announcer {

    private final BoxClansPlugin plugin;

    public PointsAnnouncer(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void gained(ClanData clan, UUID player, String display, long amount, long total) {
        String key = amount >= 0 ? (player == null ? "points-gained-clan" : "points-gained") : (player == null ? "points-lost-clan" : "points-lost");
        Object[] ph = {"amount", Math.abs(amount), "source", new Text.Mini(Text.legacyToMini(display)),
                "player", player == null ? "" : Names.of(player), "total", total, "clan", clan.name().placeholder()};
        for (Player p : Names.online(clan)) {
            plugin.messages().send(p, key, ph);
            if (amount > 0) plugin.sounds().play(p, "points-gained");
        }
    }

    @Override
    public void clanCapReached(ClanData clan, PointSource source, long cap) {
        plugin.actions().toClan(clan, "points-clan-cap", "cap", cap,
                "source", new Text.Mini(Text.legacyToMini(source.display())));
    }
}
