package com.dex.boxclans.listener;

import com.dex.boxclans.BoxClansPlugin;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.RolePermission;
import com.dex.boxclans.points.KillProcessor;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.potion.PotionType;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * PvP between clan members (§4C) and kill points (§5.2). The friendly fire check runs at {@code LOW} so
 * CrazyEnchantments (and other {@code NORMAL} listeners) see the hit already cancelled. A hit counts as the
 * player's when they caused it: melee, every projectile (arrows, tridents, splash potions, fireworks), TNT they lit,
 * an end crystal they broke, a lingering cloud they threw. Harmful splash / lingering potion effects are kept off
 * clan mates too; helpful ones (healing, speed...) still apply.
 */
public final class CombatListener implements Listener {

    private final BoxClansPlugin plugin;
    private final Predicate<PotionEffect> harmful;
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public CombatListener(BoxClansPlugin plugin) {
        this(plugin, effect -> effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL);
    }

    /** @param harmful whether an effect hurts (tests: effect types need a running server's registry) */
    public CombatListener(BoxClansPlugin plugin, Predicate<PotionEffect> harmful) {
        this.plugin = plugin;
        this.harmful = harmful;
    }

    /** The player behind a hit: the damager itself or the shooter of a projectile. */
    static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) return p;
        return null;
    }

    /** The player who caused the damage (TNT, end crystal, cloud...), else the damager or its shooter. */
    static Player attacker(EntityDamageByEntityEvent event) {
        DamageSource source = event.getDamageSource();
        if (source != null && source.getCausingEntity() instanceof Player p) return p;
        return attacker(event.getDamager());
    }

    /** The clan of {@code attacker} when it protects {@code victim} from them (same clan, PvP off), else null. */
    private ClanData protecting(Player attacker, Player victim) {
        if (attacker.equals(victim)) return null;
        ClanData clan = plugin.manager().of(attacker.getUniqueId());
        if (clan == null || clan.friendlyFire() || !clan.isMember(victim.getUniqueId())) return null;
        return clan;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(event);
        if (attacker == null) return;
        ClanData clan = protecting(attacker, victim);
        if (clan == null) return;
        event.setCancelled(true);
        warn(attacker, victim, clan);
    }

    /** Harmful splash potion: clan mates of the thrower get none of it. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower) || !anyHarmful(event.getPotion().getEffects())) return;
        for (LivingEntity target : event.getAffectedEntities()) {
            if (!(target instanceof Player victim)) continue;
            ClanData clan = protecting(thrower, victim);
            if (clan == null) continue;
            event.setIntensity(victim, 0);
            warn(thrower, victim, clan);
        }
    }

    /** Harmful lingering cloud: clan mates of the thrower are left out of every application. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player thrower) || !harmful(cloud)) return;
        event.getAffectedEntities().removeIf(target -> {
            if (!(target instanceof Player victim)) return false;
            ClanData clan = protecting(thrower, victim);
            if (clan == null) return false;
            warn(thrower, victim, clan);
            return true;
        });
    }

    private boolean harmful(AreaEffectCloud cloud) {
        PotionType base = cloud.getBasePotionType();
        return (base != null && anyHarmful(base.getPotionEffects())) || anyHarmful(cloud.getCustomEffects());
    }

    private boolean anyHarmful(Collection<PotionEffect> effects) {
        for (PotionEffect effect : effects) {
            if (harmful.test(effect)) return true;
        }
        return false;
    }

    /** The "PvP is off in your clan" warning, at most once per {@code message-cooldown}. */
    private void warn(Player attacker, Player victim, ClanData clan) {
        long now = System.currentTimeMillis();
        long cooldown = plugin.settings().friendlyFireMessageCooldownSeconds() * 1000L;
        Long last = lastWarning.get(attacker.getUniqueId());
        if (last != null && now - last < cooldown) return;
        lastWarning.put(attacker.getUniqueId(), now);
        boolean canToggle = plugin.manager().can(clan, attacker.getUniqueId(), RolePermission.MODIFY_FRIENDLY_FIRE);
        plugin.messages().send(attacker, canToggle ? "friendly-fire-blocked" : "friendly-fire-blocked-ask",
                "player", victim.getName());
        plugin.sounds().play(attacker, "friendly-fire-blocked");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        plugin.kills().handle(new KillProcessor.Kill(
                killer.getUniqueId(), ip(killer), killer.hasPermission("boxclans.antialt.bypass"),
                victim.getUniqueId(), ip(victim), victim.hasPermission("boxclans.antialt.bypass")));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastWarning.remove(event.getPlayer().getUniqueId());
    }

    private static String ip(Player p) {
        InetSocketAddress address = p.getAddress();
        return address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
    }
}
