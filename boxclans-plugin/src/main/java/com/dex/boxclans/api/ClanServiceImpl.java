package com.dex.boxclans.api;

import com.dex.boxclans.clan.ClanIndex;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.config.RoleRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** {@link ClanService} over the thread-safe {@link ClanIndex}: every method is safe from any thread. */
public final class ClanServiceImpl implements ClanService {

    private final ClanIndex index;
    private final Supplier<RoleRegistry> roles;

    public ClanServiceImpl(ClanIndex index, Supplier<RoleRegistry> roles) {
        this.index = index;
        this.roles = roles;
    }

    @Override
    public Optional<Clan> clan(UUID clanUuid) {
        return Optional.ofNullable(index.get(clanUuid));
    }

    @Override
    public Optional<Clan> clanByKey(String key) {
        return Optional.ofNullable(index.byKey(key));
    }

    @Override
    public Optional<Clan> clanOf(UUID player) {
        return Optional.ofNullable(index.of(player));
    }

    @Override
    public Optional<ClanRole> roleOf(UUID player) {
        ClanSnapshot clan = index.of(player);
        return clan == null ? Optional.empty() : Optional.ofNullable(clan.roleOf(player));
    }

    @Override
    public String roleDisplay(ClanRole role) {
        if (role == null) return "";
        RoleRegistry r = roles.get();
        return r == null ? role.key() : r.display(role);
    }

    @Override
    public String roleColor(ClanRole role) {
        if (role == null) return "";
        RoleRegistry r = roles.get();
        return r == null ? "" : r.info(role).colorLegacy();
    }

    @Override
    public Collection<Clan> clans() {
        return List.copyOf(index.all());
    }

    @Override
    public ClanRelation relation(UUID a, UUID b) {
        if (a == null || b == null) return ClanRelation.NONE;
        if (a.equals(b)) return ClanRelation.SELF;
        return sameClan(a, b) ? ClanRelation.SAME_CLAN : ClanRelation.NONE;
    }

    @Override
    public boolean sameClan(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) return false;
        ClanSnapshot clan = index.of(a);
        return clan != null && clan.members().contains(b);
    }

    @Override
    public boolean isFriendly(UUID a, UUID b) {
        if (a == null || b == null) return false;
        return a.equals(b) || sameClan(a, b);
    }

    @Override
    public List<Clan> top(int limit) {
        if (limit <= 0) return List.of();
        List<ClanSnapshot> ranked = index.ranked();
        return new ArrayList<>(ranked.subList(0, Math.min(limit, ranked.size())));
    }

    @Override
    public int rank(UUID clanUuid) {
        return clanUuid == null ? 0 : index.rank(clanUuid);
    }
}
