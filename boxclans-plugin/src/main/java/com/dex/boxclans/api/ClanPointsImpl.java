package com.dex.boxclans.api;

import com.dex.boxclans.points.PointsService;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ClanPoints} for other plugins (§3). Calls from another thread are moved to the main thread and return
 * {@link PointResult#QUEUED}; nothing ever throws.
 */
public final class ClanPointsImpl implements ClanPoints {

    /** The running kill event (§5.4), provided by the combat part. */
    public interface KillEventControl {
        boolean start(String profile, Duration duration);

        void stop();

        Optional<String> active();
    }

    public static final String BY_API = "api";

    private final Supplier<PointsService> points;
    private final BooleanSupplier mainThread;
    private final Executor toMain;
    private final Logger logger;
    private volatile KillEventControl killEvents;

    public ClanPointsImpl(Supplier<PointsService> points, BooleanSupplier mainThread, Executor toMain,
                          Logger logger) {
        this.points = points;
        this.mainThread = mainThread;
        this.toMain = toMain;
        this.logger = logger;
    }

    public void killEvents(KillEventControl control) {
        this.killEvents = control;
    }

    private PointResult run(Supplier<PointResult> call) {
        try {
            if (mainThread.getAsBoolean()) return call.get();
            toMain.execute(() -> {
                try {
                    call.get();
                } catch (RuntimeException ex) {
                        logger.log(Level.WARNING, "Clan points call failed", ex);
                }
            });
            return PointResult.QUEUED;
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "Clan points call failed", ex);
            return PointResult.NO_CLAN;
        }
    }

    @Override
    public PointResult award(UUID player, String source) {
        if (player == null || source == null) return PointResult.NO_CLAN;
        return run(() -> points.get().award(player, source, null, BY_API, false));
    }

    @Override
    public PointResult award(UUID player, String source, long amount) {
        if (player == null || source == null) return PointResult.NO_CLAN;
        return run(() -> points.get().award(player, source, amount, BY_API, false));
    }

    @Override
    public PointResult awardClan(UUID clanUuid, String source) {
        if (clanUuid == null || source == null) return PointResult.NO_CLAN;
        return run(() -> points.get().awardClan(clanUuid, source, null, BY_API, false));
    }

    @Override
    public PointResult awardClan(UUID clanUuid, String source, long amount) {
        if (clanUuid == null || source == null) return PointResult.NO_CLAN;
        return run(() -> points.get().awardClan(clanUuid, source, amount, BY_API, false));
    }

    @Override
    public boolean startKillEvent(String profile, Duration duration) {
        KillEventControl k = killEvents;
        if (k == null || profile == null || duration == null || duration.isNegative() || duration.isZero()) return false;
        if (!mainThread.getAsBoolean()) {
            toMain.execute(() -> k.start(profile, duration));
            return true;
        }
        return k.start(profile, duration);
    }

    @Override
    public void stopKillEvent() {
        KillEventControl k = killEvents;
        if (k == null) return;
        if (mainThread.getAsBoolean()) k.stop();
        else toMain.execute(k::stop);
    }

    @Override
    public Optional<String> activeKillEvent() {
        KillEventControl k = killEvents;
        return k == null ? Optional.empty() : k.active();
    }
}
