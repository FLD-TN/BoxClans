package com.dex.boxclans.hook;

import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.api.event.ClanDisbandEvent;
import com.dex.boxclans.api.event.ClanMemberJoinEvent;
import com.dex.boxclans.api.event.ClanMemberLeaveEvent;
import com.dex.boxclans.clan.ClanIndex;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.history.ClanHistory;
import com.dex.boxclans.history.HistoryType;
import com.dex.boxcore.api.BoostService;
import com.dex.boxcore.api.BottleInfo;
import com.dex.boxcore.api.ClanBoostResult;
import com.dex.boxcore.api.ClanProvider;
import com.dex.boxcore.api.event.ClanBoostEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The only class using BoxCore's clan boost API (BoxCore 1.5.0, API 1.3.0; BOXCLANS_SHOP_SPEC.md §5.3, §5.6):
 * <ul>
 *   <li>tells BoxCore who is in which clan ({@link ClanProvider}) and when that changes;</li>
 *   <li>reads bottles and starts / stops clan boosts for the storage and {@code /clanadmin boost};</li>
 *   <li>writes every clan boost change BoxCore reports ({@link ClanBoostEvent}) to the clan history, whatever
 *       caused it (a bottle in hand, the storage, a command, the boost running out).</li>
 * </ul>
 * Loaded only when BoxCore is enabled; {@link #create} returns {@code null} with an older BoxCore.
 */
public final class BoxCoreClansHook implements ClanBoosts, Listener {

    private final BoostService service;
    private final ClanIndex index;
    private final Supplier<ClanHistory.Recorder> history;

    private BoxCoreClansHook(BoostService service, ClanIndex index, Supplier<ClanHistory.Recorder> history) {
        this.service = service;
        this.index = index;
        this.history = history;
    }

    /**
     * Registers BoxClans as BoxCore's clan provider.
     *
     * @return the hook, {@code null} when BoxCore has no BoostService or is older than 1.5.0
     */
    public static BoxCoreClansHook create(Plugin plugin, ClanIndex index, Supplier<ClanHistory.Recorder> history) {
        BoostService s = Bukkit.getServicesManager().load(BoostService.class);
        if (s == null) return null;
        BoxCoreClansHook hook = new BoxCoreClansHook(s, index, history);
        s.registerClanProvider(plugin, hook.provider());
        return hook;
    }

    /** Reads the thread-safe clan index only. */
    private ClanProvider provider() {
        return new ClanProvider() {
            @Override
            public UUID clanOf(UUID player) {
                ClanSnapshot c = index.of(player);
                return c == null ? null : c.uuid();
            }

            @Override
            public boolean canUseClanBoost(UUID player) {
                ClanSnapshot c = index.of(player);
                ClanRole role = c == null ? null : c.roleOf(player);
                return role == ClanRole.LEADER || role == ClanRole.VICE;
            }

            @Override
            public String clanName(UUID clan) {
                ClanSnapshot c = index.get(clan);
                return c == null ? "" : c.name();
            }

            @Override
            public Collection<UUID> onlineMembers(UUID clan) {
                ClanSnapshot c = index.get(clan);
                if (c == null) return List.of();
                List<UUID> out = new ArrayList<>();
                for (UUID m : c.members()) {
                    Player p = Bukkit.getPlayer(m);
                    if (p != null) out.add(m);
                }
                return out;
            }
        };
    }

    // ------------------------------------------------------------------ membership changes (§E3.2)

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(ClanMemberJoinEvent event) {
        service.refreshClanOf(event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(ClanMemberLeaveEvent event) {
        service.refreshClanOf(event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisband(ClanDisbandEvent event) {
        service.clearClan(event.clan().uuid());
    }

    // ------------------------------------------------------------------ history (§6.1)

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBoost(ClanBoostEvent event) {
        HistoryType type = switch (event.action()) {
            case STARTED -> HistoryType.BOOST_START;
            case EXTENDED -> HistoryType.BOOST_EXTEND;
            case REPLACED -> HistoryType.BOOST_REPLACE;
            case STOPPED -> HistoryType.BOOST_STOP;
            case EXPIRED -> HistoryType.BOOST_END;
        };
        // detail: type id, added seconds, replaced multiplier (tenths)
        history.get().record(event.clan(), type, null, event.actor() == null ? "" : event.actor(), null,
                event.tenths(), event.type() + HistoryType.SEPARATOR + event.addedSeconds() + HistoryType.SEPARATOR
                        + event.oldTenths());
    }

    // ------------------------------------------------------------------ ClanBoosts

    @Override
    public Bottle readBottle(ItemStack item) {
        Optional<BottleInfo> info = service.readBottle(item);
        return info.map(b -> new Bottle(b.type(), b.tenths(), b.durationSeconds(), b.clan())).orElse(null);
    }

    @Override
    public Result start(UUID clan, String type, int tenths, long seconds, boolean confirmed, String actor) {
        ClanBoostResult r = service.startClanBoost(clan, type, tenths, seconds, confirmed, actor);
        return new Result(Status.valueOf(r.status().name()), r.oldTenths(), r.oldExpiresAt(), r.lostSeconds());
    }

    @Override
    public boolean stop(UUID clan, String type) {
        return service.stopClanBoost(clan, type);
    }

    @Override
    public boolean isType(String type) {
        return service.isType(type);
    }
}
