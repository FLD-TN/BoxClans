package com.dex.boxclans.command;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.api.ClanRole;
import com.dex.boxclans.api.event.ClanActionEvent;
import com.dex.boxclans.chat.ChatPrompt;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanManager;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.clan.InviteManager;
import com.dex.boxclans.clan.Outcome;
import com.dex.boxclans.clan.RolePermission;
import com.dex.boxclans.config.Icons;
import com.dex.boxclans.config.Messages;
import com.dex.boxclans.config.RoleRegistry;
import com.dex.boxclans.gui.ConfirmMenu;
import com.dex.boxclans.text.ClanName;
import com.dex.boxclans.text.ClanNameFormatter;
import com.dex.boxclans.text.Text;
import com.dex.boxclans.util.Durations;
import com.dex.boxclans.util.Names;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What players do with clans, shared by the commands and the menus: runs the {@link ClanManager} action, opens
 * the prompt or the confirmation screen when needed, and sends the messages and sounds. Main thread only.
 */
public final class ClanActions {

    private final BoxClansPlugin plugin;

    public ClanActions(BoxClansPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private ClanManager manager() {
        return plugin.manager();
    }

    // ------------------------------------------------------------------ feedback helpers

    /** Sends the error message of an outcome ({@code error-<outcome>}). */
    public void fail(CommandSender to, Outcome outcome, Object... placeholders) {
        msg().send(to, "error-" + outcome.name().toLowerCase(Locale.ROOT).replace('_', '-'), placeholders);
        if (to instanceof Player p) plugin.sounds().play(p, "error");
    }

    /** Sends a message to the online members of a clan. */
    public void toClan(ClanData clan, String key, Object... placeholders) {
        msg().send(Names.online(clan), key, placeholders);
    }

    /**
     * Role name in messages, as a placeholder value: its {@code gui-color} like the menus (1.4.3, the Minh Chủ in a
     * gold-orange gradient). Clan chat and placeholders for other plugins keep {@code color}.
     */
    public Text.Mini role(ClanRole role) {
        return guiRole(role);
    }

    /** The role as shown in menus, with its {@code gui-color}. */
    public Text.Mini guiRole(ClanRole role) {
        return new Text.Mini(plugin.roles().info(role).guiStyled());
    }

    private String duration(long seconds) {
        return Durations.format(seconds, msg());
    }

    private void nameError(Player p, ClanNameFormatter.Check check) {
        switch (check.problem()) {
            case TOO_SHORT -> msg().send(p, "name-too-short", "min", plugin.settings().nameMinLength());
            case TOO_LONG -> msg().send(p, "name-too-long", "max", plugin.settings().nameMaxLength());
            case BAD_CHARACTERS -> msg().send(p, "name-bad-characters");
            case FORBIDDEN_FORMAT -> msg().send(p, "name-forbidden-format", "detail", check.detail());
            case STYLE_PERMISSION -> msg().send(p, "name-style-permission", "style", new Text.Mini(
                    msg().text("style-labels." + check.style().name().toLowerCase(Locale.ROOT))));
            case BANNED_WORD -> msg().send(p, "name-banned-word", "word", check.detail());
        }
        plugin.sounds().play(p, "error");
    }

    private ChatPrompt.OnEnd promptEnd(Player p) {
        return reason -> {
            if (reason == ChatPrompt.End.CANCELLED) {
                msg().send(p, "prompt-cancelled");
                plugin.sounds().play(p, "prompt-cancelled");
            } else if (reason == ChatPrompt.End.TIMEOUT) {
                msg().send(p, "prompt-timeout");
            }
        };
    }

    /**
     * Asks other plugins (e.g. a running KOTH) whether the player may do this membership action; checked when the
     * action starts and again right before it runs. The canceller tells the player why.
     */
    private boolean blocked(Player p, ClanActionEvent.Action action, ClanData clan, UUID target) {
        ClanActionEvent event = new ClanActionEvent(p, action, clan == null ? null : manager().snapshot(clan), target);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) plugin.sounds().play(p, "error");
        return event.isCancelled();
    }

    /** A chat prompt for the clan shop editor (staff). */
    public void prompt(Player p, String guideKey, ChatPrompt.Handler handler, Object... placeholders) {
        openPrompt(p, ChatPrompt.Type.SHOP, handler, guideKey, placeholders);
    }

    private void openPrompt(Player p, ChatPrompt.Type type, ChatPrompt.Handler handler, String guideKey,
                            Object... placeholders) {
        p.closeInventory();
        plugin.prompts().open(p.getUniqueId(), type, handler, promptEnd(p));
        msg().send(p, guideKey, placeholders);
        plugin.sounds().play(p, "prompt-open");
    }

    // ------------------------------------------------------------------ create / disband

    /**
     * {@code /clan create [name]}: without a name asks for it in chat (§4.1); with one checks it right away. Either
     * way a valid name goes to the confirmation screen, an invalid one only gets the error.
     */
    public void startCreate(Player p, String nameArg) {
        if (!p.hasPermission("boxclans.create")) {
            msg().send(p, "no-permission");
            return;
        }
        if (manager().of(p.getUniqueId()) != null) {
            fail(p, Outcome.ALREADY_IN_CLAN);
            return;
        }
        if (blocked(p, ClanActionEvent.Action.CREATE, null, null)) return;
        if (nameArg != null && !nameArg.isBlank()) {
            confirmCreate(p, nameArg);
            return;
        }
        openPrompt(p, ChatPrompt.Type.CREATE, input -> confirmCreate(p, input),
                "prompt-create", "min", plugin.settings().nameMinLength(), "max", plugin.settings().nameMaxLength(),
                "timeout", plugin.prompts().timeoutSeconds());
    }

    /** Checks the name and opens the confirmation screen; returns false (error already sent) when it is not valid. */
    private boolean confirmCreate(Player p, String input) {
        ClanNameFormatter.Check check = ClanNameFormatter.check(input, plugin.settings(), p::hasPermission);
        if (!check.ok()) {
            nameError(p, check);
            return false;
        }
        ClanName name = check.name();
        if (manager().isKeyTaken(name.key(), null)) {
            msg().send(p, "name-taken", "key", name.key());
            plugin.sounds().play(p, "error");
            return false;
        }
        new ConfirmMenu.Request(plugin, p, "create")
                .placeholders("clan", name.placeholder(), "key", name.key())
                .onCancel(() -> msg().send(p, "action-cancelled"))
                .open(() -> create(p, name));
        return true;
    }

    private void create(Player p, ClanName name) {
        if (manager().of(p.getUniqueId()) == null && blocked(p, ClanActionEvent.Action.CREATE, null, null)) return;
        ClanManager.CreateResult r = manager().create(p.getUniqueId(), name);
        if (r.outcome() != Outcome.OK) {
            fail(p, r.outcome(), "key", name.key());
            return;
        }
        msg().send(p, "clan-created", "clan", name.placeholder(), "key", name.key());
        msg().send(Bukkit.getOnlinePlayers(), "clan-created-broadcast", "player", p.getName(), "clan", name.placeholder());
        plugin.sounds().play(p, "clan-created");
    }

    /** {@code /clan disband}: confirmation, then disband. */
    public void disband(Player p) {
        ClanData clan = manager().of(p.getUniqueId());
        if (clan == null) {
            fail(p, Outcome.NOT_IN_CLAN);
            return;
        }
        if (!clan.leader().equals(p.getUniqueId())) {
            fail(p, Outcome.NOT_LEADER);
            return;
        }
        if (blocked(p, ClanActionEvent.Action.DISBAND, clan, null)) return;
        Text.Mini name = clan.name().placeholder();
        new ConfirmMenu.Request(plugin, p, "disband").placeholders("clan", name, "key", clan.key())
                .onCancel(() -> msg().send(p, "action-cancelled"))
                .open(() -> {
                    ClanData current = manager().of(p.getUniqueId());
                    if (current != null && blocked(p, ClanActionEvent.Action.DISBAND, current, null)) return;
                    Outcome o = manager().disband(p.getUniqueId());
                    if (o != Outcome.OK) {
                        fail(p, o);
                        return;
                    }
                    msg().send(p, "clan-disbanded", "clan", name);
                    msg().send(Bukkit.getOnlinePlayers(), "clan-disbanded-broadcast", "clan", name, "player", p.getName());
                    plugin.sounds().play(p, "clan-disbanded");
                });
    }

    // ------------------------------------------------------------------ invites

    /** Asks for the name of the player to invite in chat. */
    public void startInvite(Player p) {
        ClanData clan = manager().of(p.getUniqueId());
        if (clan == null) {
            fail(p, Outcome.NOT_IN_CLAN);
            return;
        }
        if (!manager().can(clan, p.getUniqueId(), RolePermission.INVITE_MEMBERS)) {
            fail(p, Outcome.NO_PERMISSION);
            return;
        }
        if (blocked(p, ClanActionEvent.Action.INVITE, clan, null)) return;
        openPrompt(p, ChatPrompt.Type.INVITE, input -> {
            Player target = Names.online(input);
            if (target == null) {
                msg().send(p, "player-not-online", "player", input);
                plugin.sounds().play(p, "error");
                return false;
            }
            invite(p, target);
            return true;
        }, "prompt-invite", "timeout", plugin.prompts().timeoutSeconds());
    }

    public void invite(Player p, String targetName) {
        Player target = Names.online(targetName);
        if (target == null) {
            msg().send(p, "player-not-online", "player", targetName);
            return;
        }
        invite(p, target);
    }

    public void invite(Player p, Player target) {
        ClanData clan = manager().of(p.getUniqueId());
        if (clan != null && blocked(p, ClanActionEvent.Action.INVITE, clan, target.getUniqueId())) return;
        Outcome o = manager().invite(p.getUniqueId(), target.getUniqueId());
        if (o != Outcome.OK) {
            fail(p, o, "player", target.getName(),
                    "max", o == Outcome.INVITE_LIMIT ? manager().invites().maxPending() : clan == null ? 0 : clan.maxMembers());
            return;
        }
        msg().send(p, "invite-sent", "player", target.getName());
        plugin.sounds().play(p, "invite-sent");
        msg().send(target, "invite-received", "player", p.getName(), "clan", clan.name().placeholder(),
                "key", clan.key(), "expiry", duration(plugin.settings().inviteExpirySeconds()));
        plugin.sounds().play(target, "invite-received");
    }

    public void uninvite(Player p, UUID target) {
        Outcome o = manager().uninvite(p.getUniqueId(), target);
        if (o != Outcome.OK) {
            fail(p, o, "player", Names.of(target));
            return;
        }
        msg().send(p, "invite-revoked", "player", Names.of(target));
        Player t = Bukkit.getPlayer(target);
        ClanData clan = manager().of(p.getUniqueId());
        if (t != null && clan != null) msg().send(t, "invite-revoked-target", "clan", clan.name().placeholder());
    }

    /** Uninvite by name among the clan's pending invites. */
    public void uninvite(Player p, String targetName) {
        ClanData clan = manager().of(p.getUniqueId());
        if (clan == null) {
            fail(p, Outcome.NOT_IN_CLAN);
            return;
        }
        for (InviteManager.Invite i : manager().invites().ofClan(clan.uuid())) {
            if (Names.of(i.target()).equalsIgnoreCase(targetName)) {
                uninvite(p, i.target());
                return;
            }
        }
        fail(p, Outcome.NO_INVITE);
    }

    /** Resolves {@code key} (or the only invite when {@code null}) to a clan that invited the player. */
    private ClanData invitingClan(Player p, String key) {
        if (key != null) {
            ClanData clan = manager().byKey(key);
            if (clan == null) msg().send(p, "clan-not-found", "clan", key);
            return clan;
        }
        List<InviteManager.Invite> invites = manager().invites().forPlayer(p.getUniqueId());
        if (invites.isEmpty()) {
            msg().send(p, "invites-empty");
            return null;
        }
        if (invites.size() > 1) {
            msg().send(p, "invite-choose");
            return null;
        }
        return manager().clan(invites.get(0).clan());
    }

    public void accept(Player p, String key) {
        ClanData clan = invitingClan(p, key);
        if (clan != null) accept(p, clan);
    }

    public void accept(Player p, ClanData clan) {
        if (manager().of(p.getUniqueId()) == null && blocked(p, ClanActionEvent.Action.JOIN, clan, null)) return;
        Outcome o = manager().accept(p.getUniqueId(), clan.uuid());
        if (o != Outcome.OK) {
            fail(p, o, "max", clan.maxMembers());
            return;
        }
        msg().send(p, "invite-accepted", "clan", clan.name().placeholder());
        plugin.sounds().play(p, "invite-accepted");
        for (Player member : Names.online(clan)) {
            if (member.equals(p)) continue;
            msg().send(member, "member-joined", "player", p.getName());
            if (plugin.playerSettings().get(member.getUniqueId()).memberSound()) {
                plugin.sounds().play(member, "member-joined");
            }
        }
    }

    public void deny(Player p, String key) {
        ClanData clan = invitingClan(p, key);
        if (clan != null) deny(p, clan);
    }

    public void deny(Player p, ClanData clan) {
        InviteManager.Invite invite = manager().invites().get(clan.uuid(), p.getUniqueId());
        Outcome o = manager().deny(p.getUniqueId(), clan.uuid());
        if (o != Outcome.OK) {
            fail(p, o);
            return;
        }
        msg().send(p, "invite-denied", "clan", clan.name().placeholder());
        plugin.sounds().play(p, "invite-denied");
        Player inviter = invite == null ? null : Bukkit.getPlayer(invite.inviter());
        if (inviter != null) msg().send(inviter, "invite-denied-clan", "player", p.getName());
    }

    /** Lists the invites a player received (chat). */
    public void listInvites(Player p) {
        List<InviteManager.Invite> invites = manager().invites().forPlayer(p.getUniqueId());
        if (invites.isEmpty()) {
            msg().send(p, "invites-empty");
            return;
        }
        msg().send(p, "invites-header", "count", invites.size());
        long now = System.currentTimeMillis();
        for (InviteManager.Invite i : invites) {
            ClanData clan = manager().clan(i.clan());
            if (clan == null) continue;
            msg().send(p, "invites-entry", "clan", clan.name().placeholder(), "key", clan.key(),
                    "player", Names.of(i.inviter()), "expiry", duration((i.expiresAt() - now) / 1000));
        }
    }

    // ------------------------------------------------------------------ leave / kick

    public void leave(Player p) {
        ClanData clan = manager().of(p.getUniqueId());
        if (clan == null) {
            fail(p, Outcome.NOT_IN_CLAN);
            return;
        }
        if (clan.leader().equals(p.getUniqueId())) {
            fail(p, Outcome.LEADER_CANNOT_LEAVE);
            return;
        }
        if (blocked(p, ClanActionEvent.Action.LEAVE, clan, null)) return;
        Text.Mini name = clan.name().placeholder();
        new ConfirmMenu.Request(plugin, p, "leave").placeholders("clan", name)
                .onCancel(() -> msg().send(p, "action-cancelled"))
                .open(() -> {
                    ClanData current = manager().of(p.getUniqueId());
                    if (current != null && blocked(p, ClanActionEvent.Action.LEAVE, current, null)) return;
                    Outcome o = manager().leave(p.getUniqueId());
                    if (o != Outcome.OK) {
                        fail(p, o);
                        return;
                    }
                    msg().send(p, "clan-left", "clan", name);
                    toClan(clan, "member-left", "player", p.getName());
                });
    }

    /** Kick with the confirmation screen (menus) or directly (commands). */
    public void kick(Player p, UUID target, boolean confirm) {
        String targetName = Names.of(target);
        Outcome check = manager().checkKick(p.getUniqueId(), target);
        if (check != Outcome.OK) {
            fail(p, check, "player", targetName);
            return;
        }
        // Checked here for direct kicks, and both before and after the confirmation screen for menu kicks.
        if (confirm && blocked(p, ClanActionEvent.Action.KICK, manager().of(p.getUniqueId()), target)) return;
        Runnable run = () -> {
            ClanData clan = manager().of(p.getUniqueId());
            if (clan != null && blocked(p, ClanActionEvent.Action.KICK, clan, target)) return;
            Outcome o = manager().kick(p.getUniqueId(), target);
            if (o != Outcome.OK) {
                fail(p, o, "player", targetName);
                return;
            }
            toClan(clan, "member-kicked", "player", targetName, "actor", p.getName());
            plugin.sounds().play(p, "member-kicked");
        };
        if (!confirm) {
            run.run();
            return;
        }
        new ConfirmMenu.Request(plugin, p, "kick").head(Bukkit.getOfflinePlayer(target))
                .placeholders("player", targetName)
                .onCancel(() -> msg().send(p, "action-cancelled"))
                .open(run);
    }

    public void kick(Player p, String targetName) {
        ClanData clan = manager().of(p.getUniqueId());
        if (clan == null) {
            fail(p, Outcome.NOT_IN_CLAN);
            return;
        }
        UUID target = Names.member(clan, targetName);
        if (target == null) {
            fail(p, Outcome.TARGET_NOT_MEMBER, "player", targetName);
            return;
        }
        kick(p, target, false);
    }

    // ------------------------------------------------------------------ roles

    /** The Minh Chủ changes a role; a transfer or a swap of Phó Bang opens the confirmation screen. */
    public void setRole(Player p, UUID target, ClanRole role) {
        ClanManager.RoleResult r = manager().setRole(p.getUniqueId(), target, role, false);
        String targetName = Names.of(target);
        if (r.outcome() == Outcome.NEEDS_CONFIRM) {
            ClanData clan = manager().of(p.getUniqueId());
            Runnable confirmed = () -> {
                if (role == ClanRole.LEADER) {
                    ClanData current = manager().of(p.getUniqueId());
                    if (current != null && blocked(p, ClanActionEvent.Action.TRANSFER, current, target)) return;
                }
                announceRole(p, clan, p.getUniqueId(), target, role,
                        manager().setRole(p.getUniqueId(), target, role, true));
            };
            if (role == ClanRole.LEADER) {
                if (blocked(p, ClanActionEvent.Action.TRANSFER, clan, target)) return;
                ClanManager.TransferPreview preview = manager().previewTransfer(clan, target);
                ConfirmMenu.Request req = new ConfirmMenu.Request(plugin, p, "transfer-leader")
                        .head(Bukkit.getOfflinePlayer(target))
                        .placeholders("player", targetName, "limit", preview.limit(), "excess", preview.excess(),
                                "size", clan.size(), "clan", clan.name().placeholder())
                        .onCancel(() -> msg().send(p, "action-cancelled"));
                if (preview.excess() > 0) req.extra("over-limit");
                req.open(confirmed);
            } else {
                new ConfirmMenu.Request(plugin, p, "swap-vice").head(Bukkit.getOfflinePlayer(target))
                        .placeholders("player", targetName, "old", Names.of(r.displaced()))
                        .onCancel(() -> msg().send(p, "action-cancelled"))
                        .open(confirmed);
            }
            return;
        }
        announceRole(p, manager().of(p.getUniqueId()), p.getUniqueId(), target, role, r);
    }

    public void setRole(Player p, String targetName, ClanRole role) {
        ClanData clan = manager().of(p.getUniqueId());
        if (clan == null) {
            fail(p, Outcome.NOT_IN_CLAN);
            return;
        }
        UUID target = Names.member(clan, targetName);
        if (target == null) {
            fail(p, Outcome.TARGET_NOT_MEMBER, "player", targetName);
            return;
        }
        setRole(p, target, role);
    }

    /** Messages after a role change (player or admin). */
    public void announceRole(CommandSender actor, ClanData clan, UUID oldLeader, UUID target, ClanRole role,
                             ClanManager.RoleResult r) {
        String targetName = Names.of(target);
        if (r.outcome() != Outcome.OK) {
            fail(actor, r.outcome(), "player", targetName);
            return;
        }
        if (clan == null) return;
        if (role == ClanRole.LEADER) {
            toClan(clan, "leader-changed", "player", targetName, "old", Names.of(oldLeader),
                    "member_role", role(ClanRole.MEMBER));
            if (!r.kicked().isEmpty()) {
                toClan(clan, "over-limit-kicked", "limit", clan.maxMembers(),
                        "players", String.join(", ", Names.of(r.kicked())));
            }
            for (Player m : Names.online(clan)) plugin.sounds().play(m, "member-promoted");
            return;
        }
        if (r.displaced() != null) {
            toClan(clan, "role-changed", "player", Names.of(r.displaced()), "role", role(ClanRole.MEMBER));
        }
        toClan(clan, "role-changed", "player", targetName, "role", role(role));
        Player t = Bukkit.getPlayer(target);
        if (t != null) plugin.sounds().play(t, role == ClanRole.MEMBER ? "member-demoted" : "member-promoted");
    }

    // ------------------------------------------------------------------ rename / description / pvp

    /** {@code /clan rename}: same prompt and rules as creating (§4.2). */
    public void startRename(Player p) {
        ClanData clan = manager().of(p.getUniqueId());
        if (clan == null) {
            fail(p, Outcome.NOT_IN_CLAN);
            return;
        }
        if (!manager().can(clan, p.getUniqueId(), RolePermission.RENAME_CLAN)) {
            fail(p, Outcome.NO_PERMISSION);
            return;
        }
        long cooldown = plugin.settings().renameCooldownSeconds();
        openPrompt(p, ChatPrompt.Type.RENAME, input -> {
            ClanNameFormatter.Check check = ClanNameFormatter.check(input, plugin.settings(), p::hasPermission);
            if (!check.ok()) {
                nameError(p, check);
                return false;
            }
            ClanData current = manager().of(p.getUniqueId());
            if (current == null) {
                fail(p, Outcome.NOT_IN_CLAN);
                return true;
            }
            String oldName = current.name().mini();
            Outcome o = manager().rename(p.getUniqueId(), check.name(), cooldown,
                    p.hasPermission("boxclans.rename.bypasscooldown"));
            switch (o) {
                case OK -> {
                    toClan(current, "clan-renamed", "old", new Text.Mini(oldName), "clan", current.name().placeholder(),
                            "key", current.key());
                    for (Player m : Names.online(current)) plugin.sounds().play(m, "clan-renamed");
                    return true;
                }
                case RENAME_COOLDOWN -> {
                    fail(p, o, "time", duration(manager().renameCooldownLeft(current, cooldown)));
                    return false;
                }
                case NAME_TAKEN -> {
                    msg().send(p, "name-taken", "key", check.name().key());
                    plugin.sounds().play(p, "error");
                    return false;
                }
                case SAME_NAME -> {
                    fail(p, o);
                    return false;
                }
                default -> {
                    fail(p, o);
                    return true;
                }
            }
        }, "prompt-rename", "cooldown", duration(cooldown), "clan", clan.name().placeholder(), "key", clan.key(),
                "timeout", plugin.prompts().timeoutSeconds());
    }

    /** Asks for the description in chat (settings menu). */
    public void startDescription(Player p) {
        ClanData clan = manager().of(p.getUniqueId());
        if (clan == null) {
            fail(p, Outcome.NOT_IN_CLAN);
            return;
        }
        if (!manager().can(clan, p.getUniqueId(), RolePermission.SET_DESCRIPTION)) {
            fail(p, Outcome.NO_PERMISSION);
            return;
        }
        openPrompt(p, ChatPrompt.Type.DESCRIPTION, input -> setDescription(p, input),
                "prompt-description", "max", plugin.settings().descriptionMaxLength());
    }

    /** Sets the description ({@code -} clears it); returns false when it is too long (prompt asks again). */
    public boolean setDescription(Player p, String text) {
        String plain = Text.plain(Text.colorize(text == null ? "" : text)).trim();
        boolean clear = plain.isEmpty() || plain.equals("-");
        int max = plugin.settings().descriptionMaxLength();
        if (!clear && plain.codePointCount(0, plain.length()) > max) {
            msg().send(p, "description-too-long", "max", max);
            plugin.sounds().play(p, "error");
            return false;
        }
        Outcome o = manager().setDescription(p.getUniqueId(), clear ? "" : plain);
        if (o != Outcome.OK) {
            fail(p, o);
            return true;
        }
        msg().send(p, clear ? "description-cleared" : "description-updated");
        plugin.sounds().play(p, "description-updated");
        return true;
    }

    public void toggleFriendlyFire(Player p) {
        ClanData clan = manager().of(p.getUniqueId());
        if (clan == null) {
            fail(p, Outcome.NOT_IN_CLAN);
            return;
        }
        boolean enable = !clan.friendlyFire();
        Outcome o = manager().setFriendlyFire(p.getUniqueId(), enable);
        if (o != Outcome.OK) {
            fail(p, o);
            return;
        }
        toClan(clan, enable ? "friendly-fire-enabled" : "friendly-fire-disabled", "player", p.getName());
    }

    // ------------------------------------------------------------------ shield icon (BOXCLANS_ICON_SPEC.md §6)

    /** Icon name as a placeholder value: its still glyph (white) then its display name. */
    public Text.Mini iconName(Icons.Icon icon) {
        String g = plugin.iconGlyphs().stillRaw(icon.id());
        return new Text.Mini((g.isEmpty() ? "" : "<white>" + Text.MM.escapeTags(g) + "</white> ")
                + Text.legacyToMini(icon.display()));
    }

    /**
     * Shop Clan click: the Minh Chủ uses an icon, {@code ""} removes it. An icon the clan does not own yet needs
     * its permission (if any) and a price of 0 (no clan currency yet).
     */
    public void chooseIcon(Player p, String id) {
        UUID uuid = p.getUniqueId();
        ClanData clan = manager().of(uuid);
        if (clan == null) {
            fail(p, Outcome.NOT_IN_CLAN);
            return;
        }
        if (!com.dex.boxclans.shop.ShopActions.manages(clan, uuid)) {
            error(p, "icon-not-leader");
            return;
        }
        if (id.isEmpty()) {
            if (clan.icon().isEmpty()) return;
            if (iconOutcome(p, manager().setIcon(uuid, ""))) {
                toClan(clan, "icon-removed", "player", p.getName());
                plugin.sounds().play(p, "icon-changed");
            }
            return;
        }
        Icons.Icon icon = plugin.icons().icon(id);
        if (icon == null) {
            error(p, "icon-unknown", "icon", id);
            return;
        }
        if (icon.id().equals(clan.icon())) return;
        // 1.3.0: only owned icons (bought in the shop or given by staff) and the default ones can be equipped
        if (!clan.ownedIcons().contains(icon.id())) {
            if (!icon.defaultOwned()) {
                error(p, "icon-not-owned", "icon", iconName(icon));
                return;
            }
            if (!icon.permission().isEmpty() && !p.hasPermission(icon.permission())) {
                error(p, "icon-locked", "icon", iconName(icon), "permission", icon.permission());
                return;
            }
        }
        if (iconOutcome(p, manager().setIcon(uuid, icon.id()))) {
            toClan(clan, "icon-changed", "player", p.getName(), "icon", iconName(icon));
            plugin.sounds().play(p, "icon-changed");
        }
    }

    /** True on OK; a cancelled change only plays the error sound (the canceller explains). */
    private boolean iconOutcome(Player p, Outcome o) {
        if (o == Outcome.OK) return true;
        if (o == Outcome.CANCELLED) plugin.sounds().play(p, "error");
        else fail(p, o);
        return false;
    }

    private void error(Player p, String key, Object... placeholders) {
        msg().send(p, key, placeholders);
        plugin.sounds().play(p, "error");
    }

    // ------------------------------------------------------------------ chat toggles

    public void toggleChatMode(Player p) {
        if (manager().of(p.getUniqueId()) == null) {
            fail(p, Outcome.NOT_IN_CLAN);
            return;
        }
        boolean on = plugin.playerSettings().toggleChatMode(p.getUniqueId());
        msg().send(p, on ? "chat-mode-on" : "chat-mode-off");
    }

    public void toggleChatSound(Player p) {
        boolean on = plugin.playerSettings().toggleChatSound(p.getUniqueId());
        msg().send(p, on ? "chat-sound-on" : "chat-sound-off");
    }

    public void toggleMemberSound(Player p) {
        boolean on = plugin.playerSettings().toggleMemberSound(p.getUniqueId());
        msg().send(p, on ? "member-sound-on" : "member-sound-off");
    }

    // ------------------------------------------------------------------ info

    /** {@code /clan info}: the {@code info} lines of messages.yml. */
    public void sendInfo(CommandSender to, ClanSnapshot clan) {
        int online = 0;
        for (UUID m : clan.members()) if (Bukkit.getPlayer(m) != null) online++;
        String vice = clan.vice().map(Names::of).orElse(null);
        String created;
        try {
            created = new SimpleDateFormat(msg().text("date-format").isEmpty() ? "dd/MM/yyyy" : msg().text("date-format"))
                    .format(new Date(clan.createdAt()));
        } catch (IllegalArgumentException ex) {
            created = new SimpleDateFormat("dd/MM/yyyy").format(new Date(clan.createdAt()));
        }
        msg().sendList(to, "info",
                "clan", clan.clanName().placeholder(),
                "key", clan.key(),
                "leader", Names.of(clan.leader()),
                "vice", vice == null ? new Text.Mini(msg().text("none")) : vice,
                "members", clan.size(),
                "max_members", clan.maxMembers() < 0 ? msg().text("unlimited") : String.valueOf(clan.maxMembers()),
                "online", online,
                "points", clan.points(),
                "rank", plugin.index().rank(clan.uuid()),
                "pvp", new Text.Mini(msg().text(clan.friendlyFire() ? "pvp-on" : "pvp-off")),
                "created", created,
                "description", clan.description().isEmpty() ? new Text.Mini(msg().text("none")) : clan.description());
    }

    /** Chat list of every clan, by rank. */
    public void sendList(CommandSender to, int page) {
        List<ClanSnapshot> ranked = plugin.index().ranked();
        int perPage = 10;
        int pages = Math.max(1, (ranked.size() + perPage - 1) / perPage);
        int pg = Math.max(1, Math.min(page, pages));
        msg().sendList(to, "list-header", "count", ranked.size(), "page", pg, "pages", pages);
        if (ranked.isEmpty()) msg().sendList(to, "list-empty");
        for (int i = (pg - 1) * perPage; i < Math.min(ranked.size(), pg * perPage); i++) {
            ClanSnapshot c = ranked.get(i);
            msg().sendList(to, "list-entry", "index", i + 1, "clan", c.clanName().placeholder(), "key", c.key(),
                    "members", c.size(), "max_members", c.maxMembers() < 0 ? msg().text("unlimited") : String.valueOf(c.maxMembers()),
                    "points", c.points());
        }
    }

    /** Chat top 10. */
    public void sendTop(CommandSender to) {
        List<ClanSnapshot> ranked = plugin.index().ranked();
        msg().sendList(to, "top-header");
        if (ranked.isEmpty()) msg().sendList(to, "list-empty");
        for (int i = 0; i < Math.min(10, ranked.size()); i++) {
            ClanSnapshot c = ranked.get(i);
            msg().sendList(to, "top-entry", "rank", i + 1, "clan", c.clanName().placeholder(), "points", c.points(),
                    "leader", Names.of(c.leader()));
        }
    }
}
