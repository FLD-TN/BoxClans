package com.dex.boxclans.clan;

import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.text.ClanName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The live, mutable state of one clan. <b>Main thread only</b>: other threads read {@link ClanSnapshot}s from
 * {@link ClanIndex}. Only {@link ClanManager} and the points service change it.
 */
public final class ClanData {

    /** One member. */
    public static final class Member {
        private final UUID player;
        private ClanRole role;
        private final long joinedAt;
        private long contribution;
        private long lastSeen;

        public Member(UUID player, ClanRole role, long joinedAt, long contribution) {
            this.player = player;
            this.role = role;
            this.joinedAt = joinedAt;
            this.contribution = contribution;
        }

        public UUID player() {
            return player;
        }

        public ClanRole role() {
            return role;
        }

        void role(ClanRole role) {
            this.role = role;
        }

        public long joinedAt() {
            return joinedAt;
        }

        public long contribution() {
            return contribution;
        }

        public void contribution(long contribution) {
            this.contribution = contribution;
        }

        /** Last time the member was online (join or quit), epoch ms; 0 = unknown. */
        public long lastSeen() {
            return lastSeen;
        }

        public void lastSeen(long lastSeen) {
            this.lastSeen = lastSeen;
        }
    }

    private final UUID uuid;
    private ClanName name;
    private String description;
    private String flag;
    private UUID leader;
    private final long createdAt;
    private boolean friendlyFire;
    private long points;
    private int maxMembers;
    private long lastRename;
    private String icon = "";
    private final Set<String> ownedIcons = new LinkedHashSet<>();
    private long minhHuy;
    private final Map<UUID, Member> members = new LinkedHashMap<>();

    public ClanData(UUID uuid, ClanName name, String description, String flag, UUID leader, long createdAt,
                    boolean friendlyFire, long points, int maxMembers, long lastRename) {
        this.uuid = uuid;
        this.name = name;
        this.description = description == null ? "" : description;
        this.flag = flag == null ? "" : flag;
        this.leader = leader;
        this.createdAt = createdAt;
        this.friendlyFire = friendlyFire;
        this.points = points;
        this.maxMembers = maxMembers;
        this.lastRename = lastRename;
    }

    public UUID uuid() {
        return uuid;
    }

    public ClanName name() {
        return name;
    }

    public void name(ClanName name) {
        this.name = name;
    }

    public String key() {
        return name.key();
    }

    public String description() {
        return description;
    }

    public void description(String description) {
        this.description = description == null ? "" : description;
    }

    public String flag() {
        return flag;
    }

    public void flag(String flag) {
        this.flag = flag == null ? "" : flag;
    }

    public UUID leader() {
        return leader;
    }

    void leader(UUID leader) {
        this.leader = leader;
    }

    public long createdAt() {
        return createdAt;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public void friendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    public long points() {
        return points;
    }

    public void points(long points) {
        this.points = points;
    }

    /** -1 = unlimited. */
    public int maxMembers() {
        return maxMembers;
    }

    public void maxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public long lastRename() {
        return lastRename;
    }

    public void lastRename(long lastRename) {
        this.lastRename = lastRename;
    }

    /** Shield icon id in use, empty when none. */
    public String icon() {
        return icon;
    }

    public void icon(String icon) {
        this.icon = icon == null ? "" : icon;
    }

    /** Shield icon ids the clan owns (live set). */
    public Set<String> ownedIcons() {
        return ownedIcons;
    }

    /** Minh Huy balance, the currency of the whole clan (BOXCLANS_SHOP_SPEC.md §2). */
    public long minhHuy() {
        return minhHuy;
    }

    public void minhHuy(long minhHuy) {
        this.minhHuy = minhHuy;
    }

    // ------------------------------------------------------------------ members

    public Collection<Member> members() {
        return members.values();
    }

    public Member member(UUID player) {
        return members.get(player);
    }

    public boolean isMember(UUID player) {
        return members.containsKey(player);
    }

    public int size() {
        return members.size();
    }

    public ClanRole roleOf(UUID player) {
        Member m = members.get(player);
        return m == null ? null : m.role();
    }

    /** Members with a role, in join order. */
    public List<UUID> withRole(ClanRole role) {
        List<UUID> out = new ArrayList<>();
        for (Member m : members.values()) if (m.role() == role) out.add(m.player());
        return out;
    }

    /** Whether the member limit is reached ({@code -1} never is). */
    public boolean isFull() {
        return maxMembers >= 0 && members.size() >= maxMembers;
    }

    void addMember(Member member) {
        members.put(member.player(), member);
    }

    /** Used when loading from the database. */
    public void loadMember(Member member) {
        members.put(member.player(), member);
    }

    Member removeMember(UUID player) {
        return members.remove(player);
    }
}
