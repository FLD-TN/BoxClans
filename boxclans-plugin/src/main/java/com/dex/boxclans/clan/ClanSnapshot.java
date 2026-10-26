package com.dex.boxclans.clan;

import com.dex.boxclans.api.Clan;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.text.ClanName;
import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable copy of a {@link ClanData}, safe on any thread; what the API hands out. */
public final class ClanSnapshot implements Clan {

    /**
     * Role, contribution and last time online of one member at snapshot time.
     *
     * @param lastSeen epoch ms of the member's last join or quit, 0 = unknown
     */
    public record MemberInfo(ClanRole role, long joinedAt, long contribution, long lastSeen) {}

    private final UUID uuid;
    private final ClanName name;
    private final String description;
    private final String flag;
    private final UUID leader;
    private final UUID vice;
    private final Map<UUID, MemberInfo> members;
    private final long points;
    private final int maxMembers;
    private final long createdAt;
    private final boolean friendlyFire;
    private final long lastRename;
    private final String icon;
    private final Set<String> ownedIcons;
    private final long minhHuy;

    private ClanSnapshot(ClanData d) {
        this.uuid = d.uuid();
        this.name = d.name();
        this.description = d.description();
        this.flag = d.flag();
        this.leader = d.leader();
        LinkedHashMap<UUID, MemberInfo> m = new LinkedHashMap<>();
        UUID firstVice = null;
        for (ClanData.Member member : d.members()) {
            m.put(member.player(), new MemberInfo(member.role(), member.joinedAt(), member.contribution(),
                    member.lastSeen()));
            if (firstVice == null && member.role() == ClanRole.VICE) firstVice = member.player();
        }
        this.members = Collections.unmodifiableMap(m);
        this.vice = firstVice;
        this.points = d.points();
        this.maxMembers = d.maxMembers();
        this.createdAt = d.createdAt();
        this.friendlyFire = d.friendlyFire();
        this.lastRename = d.lastRename();
        this.icon = d.icon();
        this.ownedIcons = Collections.unmodifiableSet(new LinkedHashSet<>(d.ownedIcons()));
        this.minhHuy = d.minhHuy();
    }

    /** Minh Huy balance when the snapshot was taken. */
    public long minhHuy() {
        return minhHuy;
    }

    public static ClanSnapshot of(ClanData data) {
        return new ClanSnapshot(data);
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public String key() {
        return name.key();
    }

    @Override
    public String name() {
        return name.legacy();
    }

    @Override
    public Component nameComponent() {
        return name.component();
    }

    @Override
    public String plainName() {
        return name.plain();
    }

    @Override
    public String nameAmpersand() {
        return name.ampersand();
    }

    public ClanName clanName() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    public String flag() {
        return flag;
    }

    @Override
    public UUID leader() {
        return leader;
    }

    @Override
    public Optional<UUID> vice() {
        return Optional.ofNullable(vice);
    }

    @Override
    public Set<UUID> members() {
        return members.keySet();
    }

    /** Members in join order with role and contribution. */
    public Map<UUID, MemberInfo> memberInfo() {
        return members;
    }

    @Override
    public ClanRole roleOf(UUID player) {
        MemberInfo info = members.get(player);
        return info == null ? null : info.role();
    }

    @Override
    public long points() {
        return points;
    }

    @Override
    public long contribution(UUID member) {
        MemberInfo info = members.get(member);
        return info == null ? 0 : info.contribution();
    }

    @Override
    public int maxMembers() {
        return maxMembers;
    }

    @Override
    public long createdAt() {
        return createdAt;
    }

    @Override
    public boolean friendlyFire() {
        return friendlyFire;
    }

    @Override
    public String icon() {
        return icon;
    }

    @Override
    public Set<String> ownedIcons() {
        return ownedIcons;
    }

    public long lastRename() {
        return lastRename;
    }

    public int size() {
        return members.size();
    }
}
