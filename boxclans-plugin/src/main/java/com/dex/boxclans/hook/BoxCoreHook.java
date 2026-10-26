package com.dex.boxclans.hook;

import com.dex.boxclans.points.PointsService;
import com.dex.boxcore.api.BoostService;
import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * The only class importing the BoxCore API (§10.4): the multiplier of {@code boxcore-boost-type} for sources with
 * {@code boost: true}. Loaded only when BoxCore is enabled; without BoxCore every multiplier is 1.
 */
public final class BoxCoreHook implements PointsService.Booster {

    private volatile BoostService service;

    private BoxCoreHook(BoostService service) {
        this.service = service;
    }

    /** {@code null} when BoxCore does not provide the service. */
    public static BoxCoreHook create() {
        BoostService s = Bukkit.getServicesManager().load(BoostService.class);
        return s == null ? null : new BoxCoreHook(s);
    }

    @Override
    public double multiplier(UUID player, String boostType) {
        BoostService s = service;
        if (s == null || player == null || boostType == null || boostType.isEmpty()) return 1.0;
        try {
            return Math.max(1, s.getMultiplier(player, boostType));
        } catch (Throwable t) {
            return 1.0;
        }
    }

    /** Whether BoxCore declares the configured boost type (checked at start / reload to warn the owner). */
    public boolean isType(String boostType) {
        BoostService s = service;
        try {
            return s != null && s.isType(boostType);
        } catch (Throwable t) {
            return false;
        }
    }
}
