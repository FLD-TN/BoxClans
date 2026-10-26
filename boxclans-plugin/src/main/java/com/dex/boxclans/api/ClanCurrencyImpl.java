package com.dex.boxclans.api;

import com.dex.boxclans.clan.ClanIndex;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.currency.MinhHuyService;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ClanCurrency} for other plugins (BOXCLANS_SHOP_SPEC.md §2.3). Changes requested from another thread are moved
 * to the main thread and return {@link CurrencyResult#QUEUED}; nothing ever throws. The history shows {@code api} as
 * the author.
 */
public final class ClanCurrencyImpl implements ClanCurrency {

    public static final String BY_API = "api";

    private final Supplier<MinhHuyService> service;
    private final ClanIndex index;
    private final BooleanSupplier mainThread;
    private final Executor toMain;
    private final Logger logger;

    public ClanCurrencyImpl(Supplier<MinhHuyService> service, ClanIndex index, BooleanSupplier mainThread,
                            Executor toMain, Logger logger) {
        this.service = service;
        this.index = index;
        this.mainThread = mainThread;
        this.toMain = toMain;
        this.logger = logger;
    }

    private CurrencyResult run(Supplier<CurrencyResult> call) {
        try {
            if (mainThread.getAsBoolean()) return call.get();
            toMain.execute(() -> {
                try {
                    call.get();
                } catch (RuntimeException ex) {
                    logger.log(Level.WARNING, "Minh Huy call failed", ex);
                }
            });
            return CurrencyResult.QUEUED;
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "Minh Huy call failed", ex);
            return CurrencyResult.NO_CLAN;
        }
    }

    @Override
    public long balance(UUID clanUuid) {
        ClanSnapshot clan = clanUuid == null ? null : index.get(clanUuid);
        return clan == null ? 0 : clan.minhHuy();
    }

    @Override
    public CurrencyResult give(UUID player, long amount, String reason) {
        if (player == null) return CurrencyResult.NO_CLAN;
        return run(() -> {
            MinhHuyService s = service.get();
            return s.give(s.clanOf(player), amount, MinhHuyService.reason(reason, BY_API), BY_API, false);
        });
    }

    @Override
    public CurrencyResult giveClan(UUID clanUuid, long amount, String reason) {
        if (clanUuid == null) return CurrencyResult.NO_CLAN;
        return run(() -> {
            MinhHuyService s = service.get();
            return s.give(s.clan(clanUuid), amount, MinhHuyService.reason(reason, BY_API), BY_API, false);
        });
    }

    @Override
    public CurrencyResult take(UUID clanUuid, long amount, String reason) {
        if (clanUuid == null) return CurrencyResult.NO_CLAN;
        return run(() -> {
            MinhHuyService s = service.get();
            return s.take(s.clan(clanUuid), amount, MinhHuyService.reason(reason, BY_API), BY_API, false);
        });
    }
}
