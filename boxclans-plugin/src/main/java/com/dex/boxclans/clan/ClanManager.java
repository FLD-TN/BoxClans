package com.dex.boxclans.clan;

import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.api.event.ClanCreateEvent;
import com.dex.boxclans.api.event.ClanDisbandEvent;
import com.dex.boxclans.api.event.ClanFriendlyFireChangeEvent;
import com.dex.boxclans.api.event.ClanIconChangeEvent;
import com.dex.boxclans.api.event.ClanLeaderChangeEvent;
import com.dex.boxclans.api.event.ClanMemberJoinEvent;
import com.dex.boxclans.api.event.ClanMemberLeaveEvent;
import com.dex.boxclans.api.event.ClanRenameEvent;
import com.dex.boxclans.api.event.ClanRoleChangeEvent;
import com.dex.boxclans.config.RoleRegistry;
import com.dex.boxclans.history.ClanHistory;
import com.dex.boxclans.history.HistoryType;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.ClanName;
import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

/**
 * Every change to clans and memberships (§4A, §6). <b>Main thread only.</b> After each change the state is
 * published to {@link ClanIndex} (so API readers see it), queued to the {@link Database}, and only then the
 * matching event is fired. Personal news for a player who may be offline (kicked, clan disbanded) goes through
 * {@link Notices}.
 */
public final class ClanManager {

    /** Fires a Bukkit event (synchronous) and returns it. */
    @FunctionalInterface
    public interface Events {
        <T extends Event> T call(T event);
    }

    /** Sends a {@code messages.yml} message to a player now, or at their next login when offline. */
    @FunctionalInterface
    public interface Notices {
        void send(UUID player, String key, Object... placeholders);
    }

    public record CreateResult(Outcome outcome, ClanData clan) {}

    /**
     * @param displaced the Phó Bang moved back to Thành Viên (swap), or the one that would be (needs confirm)
     * @param kicked    members kicked at random after a leader transfer (§6)
     */
    public record RoleResult(Outcome outcome, UUID displaced, List<UUID> kicked) {
        static RoleResult of(Outcome outcome) {
            return new RoleResult(outcome, null, List.of());
        }
    }

    /** What a leader transfer would do: the new limit and how many members would be kicked at random. */
    public record TransferPreview(int limit, int excess) {}

    private final ClanIndex index;
    private final Database db;
    private final InviteManager invites;
    private final MemberLimit limits;
    private final Events events;
    private final Notices notices;
    private final LongSupplier clock;
    private final Random random;
    private RoleRegistry roles;
    private boolean defaultFriendlyFire;
    private String defaultIcon = "";
    private ClanHistory.Recorder history = ClanHistory.Recorder.NONE;

    private final Map<UUID, ClanData> clans = new HashMap<>();
    private final Map<String, ClanData> byKey = new HashMap<>();
    private final Map<UUID, ClanData> byPlayer = new HashMap<>();

    public ClanManager(ClanIndex index, Database db, InviteManager invites, MemberLimit limits, RoleRegistry roles,
                       boolean defaultFriendlyFire, Events events, Notices notices, LongSupplier clock, Random random) {
        this.index = index;
        this.db = db;
        this.invites = invites;
        this.limits = limits;
        this.roles = roles;
        this.defaultFriendlyFire = defaultFriendlyFire;
        this.events = events;
        this.notices = notices;
        this.clock = clock;
        this.random = random;
    }

    /** After a reload. */
    public void reconfigure(RoleRegistry roles, boolean defaultFriendlyFire) {
        this.roles = roles;
        this.defaultFriendlyFire = defaultFriendlyFire;
    }

    /** Where clan history entries go (BOXCLANS_SHOP_SPEC.md §6.1); nothing is recorded until set. */
    public void history(ClanHistory.Recorder history) {
        this.history = history == null ? ClanHistory.Recorder.NONE : history;
    }

    public ClanHistory.Recorder history() {
        return history;
    }

    /** Shield icon of new clans ({@code icons.yml default-icon}), {@code ""} = none. */
    public void defaultIcon(String icon) {
        this.defaultIcon = icon == null ? "" : icon;
    }

    /** Fills the manager and the index with the clans read at start-up. */
    public void load(Collection<ClanData> loaded) {
        clans.clear();
        byKey.clear();
        byPlayer.clear();
        index.clear();
        for (ClanData clan : loaded) {
            register(clan);
            index.publish(clan);
        }
    }

    // ------------------------------------------------------------------ lookups

    public ClanData clan(UUID uuid) {
        return uuid == null ? null : clans.get(uuid);
    }

    public ClanData byKey(String key) {
        return key == null ? null : byKey.get(lower(key));
    }

    public ClanData of(UUID player) {
        return player == null ? null : byPlayer.get(player);
    }

    public Collection<ClanData> clans() {
        return clans.values();
    }

    public InviteManager invites() {
        return invites;
    }

    public RoleRegistry roles() {
        return roles;
    }

    /** Whether the key is used by a clan other than {@code except}. */
    public boolean isKeyTaken(String key, UUID except) {
        ClanData other = byKey(key);
        return other != null && !other.uuid().equals(except);
    }

    /** Whether a member's role holds a permission. */
    public boolean can(ClanData clan, UUID player, RolePermission permission) {
        ClanRole role = clan.roleOf(player);
        return role != null && roles.has(role, permission);
    }

    /** The published snapshot of a clan (publishes it if missing). */
    public ClanSnapshot snapshot(ClanData clan) {
        ClanSnapshot snap = index.get(clan.uuid());
        return snap != null ? snap : index.publish(clan);
    }

    /** Publishes the current state of a clan to the read index. */
    public ClanSnapshot publish(ClanData clan) {
        return index.publish(clan);
    }

    private void register(ClanData clan) {
        clans.put(clan.uuid(), clan);
        byKey.put(lower(clan.key()), clan);
        for (ClanData.Member m : clan.members()) byPlayer.put(m.player(), clan);
    }

    private static String lower(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ create / disband

    public CreateResult create(UUID leader, ClanName name) {
        if (byPlayer.containsKey(leader)) return new CreateResult(Outcome.ALREADY_IN_CLAN, null);
        if (byKey.containsKey(lower(name.key()))) return new CreateResult(Outcome.NAME_TAKEN, null);
        long now = clock.getAsLong();
        int limit = limits.of(leader).orElse(limits.perks().defaultMaxMembers());
        ClanData clan = new ClanData(UUID.randomUUID(), name, "", "", leader, now, defaultFriendlyFire, 0, limit, 0);
        if (!defaultIcon.isEmpty()) {
            clan.icon(defaultIcon);
            clan.ownedIcons().add(defaultIcon);
        }
        ClanData.Member member = new ClanData.Member(leader, ClanRole.LEADER, now, 0);
        member.lastSeen(now);
        clan.addMember(member);
        register(clan);
        invites.clearPlayer(leader);
        db.saveClan(clan);
        db.saveMember(clan.uuid(), member);
        ClanSnapshot snap = publish(clan);
        history.record(clan.uuid(), HistoryType.CREATE, leader, null, null, 0, name.raw());
        events.call(new ClanCreateEvent(snap, leader));
        return new CreateResult(Outcome.OK, clan);
    }

    /** The Minh Chủ disbands their clan. */
    public Outcome disband(UUID actor) {
        ClanData clan = of(actor);
        if (clan == null) return Outcome.NOT_IN_CLAN;
        if (!clan.leader().equals(actor)) return Outcome.NOT_LEADER;
        disband(clan, actor);
        return Outcome.OK;
    }

    /** Disbands a clan; {@code actor} is {@code null} for the console / an admin. */
    public void disband(ClanData clan, UUID actor) {
        ClanSnapshot last = publish(clan);
        clans.remove(clan.uuid());
        byKey.remove(lower(clan.key()), clan);
        List<UUID> members = new ArrayList<>();
        for (ClanData.Member m : clan.members()) {
            byPlayer.remove(m.player(), clan);
            members.add(m.player());
        }
        invites.clearClan(clan.uuid());
        index.remove(clan.uuid());
        db.deleteClan(clan.uuid());
        for (UUID p : members) {
            events.call(new ClanMemberLeaveEvent(last, p, ClanMemberLeaveEvent.Reason.DISBAND));
            if (!p.equals(actor)) notices.send(p, "notice-clan-disbanded", "clan", clan.name().placeholder());
        }
        events.call(new ClanDisbandEvent(last, actor));
    }

    // ------------------------------------------------------------------ invites

    public Outcome invite(UUID actor, UUID target) {
        ClanData clan = of(actor);
        if (clan == null) return Outcome.NOT_IN_CLAN;
        if (!can(clan, actor, RolePermission.INVITE_MEMBERS)) return Outcome.NO_PERMISSION;
        if (actor.equals(target)) return Outcome.SELF;
        if (byPlayer.containsKey(target)) return Outcome.TARGET_IN_CLAN;
        if (invites.has(clan.uuid(), target)) return Outcome.ALREADY_INVITED;
        refreshLimit(clan);
        if (clan.isFull()) return Outcome.CLAN_FULL;
        if (invites.isFull(clan.uuid())) return Outcome.INVITE_LIMIT;
        invites.add(clan.uuid(), target, actor);
        return Outcome.OK;
    }

    public Outcome uninvite(UUID actor, UUID target) {
        ClanData clan = of(actor);
        if (clan == null) return Outcome.NOT_IN_CLAN;
        if (!can(clan, actor, RolePermission.INVITE_MEMBERS)) return Outcome.NO_PERMISSION;
        return invites.remove(clan.uuid(), target) == null ? Outcome.NO_INVITE : Outcome.OK;
    }

    public Outcome accept(UUID player, UUID clanUuid) {
        if (byPlayer.containsKey(player)) return Outcome.ALREADY_IN_CLAN;
        ClanData clan = clans.get(clanUuid);
        if (clan == null) return Outcome.UNKNOWN_CLAN;
        if (invites.get(clanUuid, player) == null) return Outcome.NO_INVITE;
        refreshLimit(clan);
        if (clan.isFull()) return Outcome.CLAN_FULL;
        invites.clearPlayer(player);
        ClanData.Member member = new ClanData.Member(player, ClanRole.MEMBER, clock.getAsLong(), 0);
        member.lastSeen(member.joinedAt());
        clan.addMember(member);
        byPlayer.put(player, clan);
        db.saveMember(clan.uuid(), member);
        ClanSnapshot snap = publish(clan);
        history.record(clan.uuid(), HistoryType.JOIN, player, null);
        events.call(new ClanMemberJoinEvent(snap, player));
        return Outcome.OK;
    }

    public Outcome deny(UUID player, UUID clanUuid) {
        return invites.remove(clanUuid, player) == null ? Outcome.NO_INVITE : Outcome.OK;
    }

    // ------------------------------------------------------------------ leave / kick

    public Outcome leave(UUID player) {
        ClanData clan = of(player);
        if (clan == null) return Outcome.NOT_IN_CLAN;
        if (clan.leader().equals(player)) return Outcome.LEADER_CANNOT_LEAVE;
        removeMember(clan, player, ClanMemberLeaveEvent.Reason.LEAVE);
        history.record(clan.uuid(), HistoryType.LEAVE, player, null);
        return Outcome.OK;
    }

    public Outcome kick(UUID actor, UUID target) {
        Outcome check = checkKick(actor, target);
        if (check != Outcome.OK) return check;
        kickMember(of(actor), target, actor);
        return Outcome.OK;
    }

    /** Whether {@code actor} may kick {@code target}, without kicking (for the confirmation screen). */
    public Outcome checkKick(UUID actor, UUID target) {
        ClanData clan = of(actor);
        if (clan == null) return Outcome.NOT_IN_CLAN;
        if (!can(clan, actor, RolePermission.KICK_MEMBERS)) return Outcome.NO_PERMISSION;
        if (actor.equals(target)) return Outcome.SELF;
        if (!clan.isMember(target)) return Outcome.TARGET_NOT_MEMBER;
        ClanRole targetRole = clan.roleOf(target);
        if (targetRole == ClanRole.LEADER) return Outcome.TARGET_IS_LEADER;
        if (clan.roleOf(actor) != ClanRole.LEADER && targetRole != ClanRole.MEMBER) return Outcome.TARGET_HIGHER;
        return Outcome.OK;
    }

    /** Admin kick (any member but the Minh Chủ). */
    public Outcome adminKick(UUID target) {
        ClanData clan = of(target);
        if (clan == null) return Outcome.NOT_IN_CLAN;
        if (clan.leader().equals(target)) return Outcome.TARGET_IS_LEADER;
        kickMember(clan, target, null);
        return Outcome.OK;
    }

    /** @param actor who kicks, {@code null} for staff */
    private void kickMember(ClanData clan, UUID target, UUID actor) {
        removeMember(clan, target, ClanMemberLeaveEvent.Reason.KICK);
        history.record(clan.uuid(), HistoryType.KICK, actor, target);
        notices.send(target, "notice-kicked", "clan", clan.name().placeholder());
    }

    private void removeMember(ClanData clan, UUID player, ClanMemberLeaveEvent.Reason reason) {
        clan.removeMember(player);
        byPlayer.remove(player, clan);
        db.deleteMember(player);
        ClanSnapshot snap = publish(clan);
        events.call(new ClanMemberLeaveEvent(snap, player, reason));
    }

    // ------------------------------------------------------------------ roles

    /**
     * The Minh Chủ changes a member's role (§4A). {@code LEADER} transfers the clan; both a transfer and a swap
     * of Phó Bang return {@link Outcome#NEEDS_CONFIRM} until called again with {@code confirmed}.
     */
    public RoleResult setRole(UUID actor, UUID target, ClanRole role, boolean confirmed) {
        ClanData clan = of(actor);
        if (clan == null) return RoleResult.of(Outcome.NOT_IN_CLAN);
        if (!clan.leader().equals(actor)) return RoleResult.of(Outcome.NOT_LEADER);
        if (actor.equals(target)) return RoleResult.of(Outcome.SELF);
        if (!clan.isMember(target)) return RoleResult.of(Outcome.TARGET_NOT_MEMBER);
        return applyRole(clan, target, role, confirmed, actor);
    }

    /** Admin version of {@link #setRole} (console allowed). */
    public RoleResult adminSetRole(UUID target, ClanRole role, boolean confirmed) {
        ClanData clan = of(target);
        if (clan == null) return RoleResult.of(Outcome.NOT_IN_CLAN);
        return applyRole(clan, target, role, confirmed, null);
    }

    /** @param actor who changes the role, {@code null} for staff */
    private RoleResult applyRole(ClanData clan, UUID target, ClanRole role, boolean confirmed, UUID actor) {
        ClanRole current = clan.roleOf(target);
        if (current == role) return RoleResult.of(Outcome.SAME_ROLE);
        switch (role) {
            case LEADER -> {
                if (!confirmed) return RoleResult.of(Outcome.NEEDS_CONFIRM);
                return transfer(clan, target, actor == null);
            }
            case VICE -> {
                if (current == ClanRole.LEADER) return RoleResult.of(Outcome.TARGET_IS_LEADER);
                int max = roles.maxVice();
                if (max == 0) return RoleResult.of(Outcome.ROLE_DISABLED);
                List<UUID> vices = clan.withRole(ClanRole.VICE);
                UUID displaced = null;
                if (max > 0 && vices.size() >= max) {
                    displaced = vices.get(0);
                    if (!confirmed) return new RoleResult(Outcome.NEEDS_CONFIRM, displaced, List.of());
                    changeRole(clan, displaced, ClanRole.MEMBER, actor);
                }
                changeRole(clan, target, ClanRole.VICE, actor);
                return new RoleResult(Outcome.OK, displaced, List.of());
            }
            default -> {
                if (current == ClanRole.LEADER) return RoleResult.of(Outcome.TARGET_IS_LEADER);
                changeRole(clan, target, ClanRole.MEMBER, actor);
                return RoleResult.of(Outcome.OK);
            }
        }
    }

    private void changeRole(ClanData clan, UUID player, ClanRole role, UUID actor) {
        ClanData.Member m = clan.member(player);
        ClanRole old = m.role();
        m.role(role);
        db.saveMember(clan.uuid(), m);
        ClanSnapshot snap = publish(clan);
        history.record(clan.uuid(), HistoryType.ROLE, actor, null, player, 0, old.key() + ">" + role.key());
        events.call(new ClanRoleChangeEvent(snap, player, old, role));
    }

    /** What transferring the clan to {@code newLeader} would do (§6, confirmation screen). */
    public TransferPreview previewTransfer(ClanData clan, UUID newLeader) {
        int limit = limits.of(newLeader).orElse(clan.maxMembers());
        int candidates = Math.max(0, clan.size() - 2);
        return new TransferPreview(limit, Math.min(MemberLimit.excess(clan.size(), limit), candidates));
    }

    /**
     * Transfers the clan (§4A, §6): the old Minh Chủ becomes Thành Viên, the Phó Bang keeps the role (unless they
     * are the new leader), the limit becomes the new leader's, and the excess is kicked at random, never the old
     * or the new leader. An offline new leader keeps the current limit (their permissions cannot be read).
     */
    /** @param byStaff an admin command, not the Minh Chủ */
    private RoleResult transfer(ClanData clan, UUID target, boolean byStaff) {
        UUID old = clan.leader();
        ClanData.Member newLeader = clan.member(target);
        ClanData.Member oldLeader = clan.member(old);
        ClanRole targetRole = newLeader.role();
        OptionalInt limit = limits.of(target);
        clan.leader(target);
        newLeader.role(ClanRole.LEADER);
        oldLeader.role(ClanRole.MEMBER);
        if (limit.isPresent()) clan.maxMembers(limit.getAsInt());
        db.saveClan(clan);
        db.saveMember(clan.uuid(), newLeader);
        db.saveMember(clan.uuid(), oldLeader);
        ClanSnapshot snap = publish(clan);
        history.record(clan.uuid(), HistoryType.LEADER, old, null, target, byStaff ? 1 : 0, "");
        events.call(new ClanRoleChangeEvent(snap, target, targetRole, ClanRole.LEADER));
        events.call(new ClanRoleChangeEvent(snap, old, ClanRole.LEADER, ClanRole.MEMBER));
        events.call(new ClanLeaderChangeEvent(snap, old, target));
        List<UUID> candidates = new ArrayList<>();
        for (ClanData.Member m : clan.members()) {
            if (!m.player().equals(old) && !m.player().equals(target)) candidates.add(m.player());
        }
        List<UUID> kicked = kickExcess(clan, candidates);
        return new RoleResult(Outcome.OK, null, kicked);
    }

    /** Admin: kicks members at random until the clan fits its limit (never the Minh Chủ). */
    public List<UUID> trim(ClanData clan) {
        refreshLimit(clan);
        List<UUID> candidates = new ArrayList<>();
        for (ClanData.Member m : clan.members()) if (!m.player().equals(clan.leader())) candidates.add(m.player());
        return kickExcess(clan, candidates);
    }

    private List<UUID> kickExcess(ClanData clan, List<UUID> candidates) {
        int excess = MemberLimit.excess(clan.size(), clan.maxMembers());
        if (excess == 0) return List.of();
        List<UUID> kicked = MemberLimit.pickRandom(candidates, excess, random);
        for (UUID p : kicked) {
            removeMember(clan, p, ClanMemberLeaveEvent.Reason.OVER_LIMIT);
            history.record(clan.uuid(), HistoryType.KICK_OVER_LIMIT, null, p);
            notices.send(p, "notice-kicked-over-limit", "clan", clan.name().placeholder(), "limit", clan.maxMembers());
        }
        return kicked;
    }

    // ------------------------------------------------------------------ rename (§4.2)

    /**
     * A member with {@code RENAME_CLAN} renames the clan. Changing only the colours (same command key) is free;
     * changing the text needs {@code cooldownSeconds} since the last text change unless {@code bypassCooldown}.
     */
    public Outcome rename(UUID actor, ClanName name, long cooldownSeconds, boolean bypassCooldown) {
        ClanData clan = of(actor);
        if (clan == null) return Outcome.NOT_IN_CLAN;
        if (!can(clan, actor, RolePermission.RENAME_CLAN)) return Outcome.NO_PERMISSION;
        if (keyChanges(clan, name) && !bypassCooldown && renameCooldownLeft(clan, cooldownSeconds) > 0) {
            return Outcome.RENAME_COOLDOWN;
        }
        return applyRename(clan, name, actor);
    }

    /** Admin rename: no cooldown. */
    public Outcome adminRename(ClanData clan, ClanName name) {
        return applyRename(clan, name, null);
    }

    /** Whether the new name changes the command key (text change, not only colours). */
    public static boolean keyChanges(ClanData clan, ClanName name) {
        return !clan.key().equalsIgnoreCase(name.key());
    }

    /** Seconds before the clan may change its text again, 0 when allowed. */
    public long renameCooldownLeft(ClanData clan, long cooldownSeconds) {
        if (clan.lastRename() <= 0) return 0;
        long left = clan.lastRename() + cooldownSeconds * 1000L - clock.getAsLong();
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    /** @param actor who renames, {@code null} for staff */
    private Outcome applyRename(ClanData clan, ClanName name, UUID actor) {
        if (clan.name().raw().equals(name.raw())) return Outcome.SAME_NAME;
        boolean keyChanged = keyChanges(clan, name);
        if (keyChanged && isKeyTaken(name.key(), clan.uuid())) return Outcome.NAME_TAKEN;
        String oldRaw = clan.name().raw();
        String oldName = clan.name().legacy();
        String oldKey = clan.key();
        byKey.remove(lower(oldKey), clan);
        clan.name(name);
        byKey.put(lower(name.key()), clan);
        if (keyChanged) clan.lastRename(clock.getAsLong());
        db.saveClan(clan);
        ClanSnapshot snap = publish(clan);
        history.record(clan.uuid(), HistoryType.RENAME, actor, null, null, 0, oldRaw + HistoryType.SEPARATOR + name.raw());
        events.call(new ClanRenameEvent(snap, oldName, oldKey));
        return Outcome.OK;
    }

    // ------------------------------------------------------------------ limit, settings

    /** Re-reads the leader's member limit when the leader is online; stores it if it changed. */
    public void refreshLimit(ClanData clan) {
        OptionalInt limit = limits.of(clan.leader());
        if (limit.isPresent() && limit.getAsInt() != clan.maxMembers()) {
            clan.maxMembers(limit.getAsInt());
            db.saveClan(clan);
            publish(clan);
        }
    }

    /** A player joined the server: last seen is now, a leader's limit is re-read (§6). */
    public void onJoin(UUID player) {
        ClanData clan = of(player);
        if (clan == null) return;
        seen(player);
        if (clan.leader().equals(player)) refreshLimit(clan);
    }

    /**
     * Records that a member is online right now ("online lần cuối" in the member menu). Called on join, on quit and
     * for everyone still online when the plugin stops; a crash keeps the last join time.
     */
    public void seen(UUID player) {
        ClanData clan = of(player);
        if (clan == null) return;
        ClanData.Member m = clan.member(player);
        if (m == null) return;
        m.lastSeen(clock.getAsLong());
        db.saveMember(clan.uuid(), m);
        publish(clan);
    }

    /**
     * One-off migration for members stored before {@code last_seen} existed: they take the server's own record
     * ({@code serverLastSeen}, 0 when unknown). Main thread; returns how many members were filled in.
     */
    public int backfillLastSeen(ToLongFunction<UUID> serverLastSeen) {
        int filled = 0;
        for (ClanData clan : clans.values()) {
            boolean changed = false;
            for (ClanData.Member m : clan.members()) {
                if (m.lastSeen() > 0) continue;
                long at = serverLastSeen.applyAsLong(m.player());
                if (at <= 0) continue;
                m.lastSeen(at);
                db.saveMember(clan.uuid(), m);
                changed = true;
                filled++;
            }
            if (changed) publish(clan);
        }
        return filled;
    }

    public Outcome setDescription(UUID actor, String description) {
        ClanData clan = of(actor);
        if (clan == null) return Outcome.NOT_IN_CLAN;
        if (!can(clan, actor, RolePermission.SET_DESCRIPTION)) return Outcome.NO_PERMISSION;
        clan.description(description);
        db.saveClan(clan);
        publish(clan);
        return Outcome.OK;
    }

    public Outcome setFlag(UUID actor, String flag) {
        ClanData clan = of(actor);
        if (clan == null) return Outcome.NOT_IN_CLAN;
        if (!can(clan, actor, RolePermission.EDIT_FLAG)) return Outcome.NO_PERMISSION;
        clan.flag(flag);
        db.saveClan(clan);
        publish(clan);
        return Outcome.OK;
    }

    // ------------------------------------------------------------------ shield icon

    /**
     * The Minh Chủ or the Phó Bang uses a shield icon ({@code ""} removes it). The caller checks that the icon exists
     * and may be taken (owned, default, permission); using an icon makes the clan own it.
     */
    public Outcome setIcon(UUID actor, String icon) {
        ClanData clan = of(actor);
        if (clan == null) return Outcome.NOT_IN_CLAN;
        ClanRole role = clan.roleOf(actor);
        if (role != ClanRole.LEADER && role != ClanRole.VICE) return Outcome.NOT_LEADER;
        return applyIcon(clan, icon, actor);
    }

    /** Admin: uses an icon (and owns it), or removes it with {@code ""}. */
    public Outcome adminSetIcon(ClanData clan, String icon) {
        return applyIcon(clan, icon, null);
    }

    private Outcome applyIcon(ClanData clan, String icon, UUID actor) {
        String next = icon == null ? "" : icon;
        if (clan.icon().equals(next)) return Outcome.OK;
        if (events.call(new ClanIconChangeEvent(snapshot(clan), clan.icon(), next, actor)).isCancelled()) {
            return Outcome.CANCELLED;
        }
        String old = clan.icon();
        clan.icon(next);
        if (!next.isEmpty()) clan.ownedIcons().add(next);
        db.saveClan(clan);
        publish(clan);
        history.record(clan.uuid(), HistoryType.ICON, actor, null, null, 0, old + HistoryType.SEPARATOR + next);
        return Outcome.OK;
    }

    /** Admin: the clan owns an icon without using it. False when it already did. */
    public boolean unlockIcon(ClanData clan, String icon) {
        if (!clan.ownedIcons().add(icon)) return false;
        db.saveClan(clan);
        publish(clan);
        return true;
    }

    /** Admin: takes an icon back; a clan using it goes back to no icon. False when it did not own it. */
    public boolean lockIcon(ClanData clan, String icon) {
        boolean owned = clan.ownedIcons().remove(icon);
        boolean used = clan.icon().equals(icon);
        if (!owned && !used) return false;
        if (used) clan.icon("");
        db.saveClan(clan);
        publish(clan);
        return true;
    }

    public Outcome setFriendlyFire(UUID actor, boolean enabled) {
        ClanData clan = of(actor);
        if (clan == null) return Outcome.NOT_IN_CLAN;
        if (!can(clan, actor, RolePermission.MODIFY_FRIENDLY_FIRE)) return Outcome.NO_PERMISSION;
        if (clan.friendlyFire() == enabled) return Outcome.OK;
        clan.friendlyFire(enabled);
        db.saveClan(clan);
        ClanSnapshot snap = publish(clan);
        history.record(clan.uuid(), HistoryType.PVP, actor, null, null, enabled ? 1 : 0, "");
        events.call(new ClanFriendlyFireChangeEvent(snap, enabled, actor));
        return Outcome.OK;
    }
}
