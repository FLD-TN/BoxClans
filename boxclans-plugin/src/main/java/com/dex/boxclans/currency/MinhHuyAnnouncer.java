package com.dex.boxclans.currency;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.util.Names;
import com.dex.boxclans.util.Numbers;

/** {@link MinhHuyService.Announcer} with {@code messages.yml}: {@code minhhuy-gained}, {@code minhhuy-lost}. */
public final class MinhHuyAnnouncer implements MinhHuyService.Announcer {

    private final BoxClansPlugin plugin;

    public MinhHuyAnnouncer(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void changed(ClanData clan, long delta, long balance, String reason) {
        plugin.actions().toClan(clan, delta > 0 ? "minhhuy-gained" : "minhhuy-lost",
                "amount", Numbers.grouped(Math.abs(delta)), "balance", Numbers.grouped(balance),
                "reason", reason.isEmpty() ? "-" : reason, "clan", clan.name().placeholder());
        if (delta > 0) plugin.sounds().play(Names.online(clan), "points-gained");
    }
}
