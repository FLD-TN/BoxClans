package com.dex.boxclans.currency;

import com.dex.boxclans.api.ClanCurrency;
import com.dex.boxclans.api.CurrencyResult;
import com.dex.boxclans.api.CurrencyResult.Status;
import com.dex.boxclans.api.event.ClanMinhHuyChangeEvent;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.clan.ClanManager;
import com.dex.boxclans.clan.ClanSnapshot;
import com.dex.boxclans.history.HistoryType;
import com.dex.boxclans.storage.Database;

import java.util.UUID;

/**
 * Minh Huy, the currency of a whole clan (BOXCLANS_SHOP_SPEC.md §2). Every change is checked (positive amounts, never
 * below 0, never above {@link ClanCurrency#MAX_BALANCE}), stored, kept in the clan history, announced to the clan
 * unless silent, then {@link ClanMinhHuyChangeEvent} is fired. Main thread only.
 */
public final class MinhHuyService {

    /** Tells the clan's online members about a gain or a loss. */
    public interface Announcer {
        void changed(ClanData clan, long delta, long balance, String reason);
    }

    /** Longest reason kept (history, messages). */
    public static final int MAX_REASON = 64;

    private final ClanManager manager;
    private final Database db;
    private final ClanManager.Events events;
    private final Announcer announcer;

    public MinhHuyService(ClanManager manager, Database db, ClanManager.Events events, Announcer announcer) {
        this.manager = manager;
        this.db = db;
        this.events = events;
        this.announcer = announcer;
    }

    /** Trims and shortens a reason; empty becomes {@code fallback}. */
    public static String reason(String reason, String fallback) {
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) return fallback;
        return r.length() > MAX_REASON ? r.substring(0, MAX_REASON) : r;
    }

    /**
     * Adds Minh Huy.
     *
     * @param by     who did it, shown in the history (a player or "console")
     * @param silent true: the clan is not told
     */
    public CurrencyResult give(ClanData clan, long amount, String reason, String by, boolean silent) {
        if (clan == null) return CurrencyResult.NO_CLAN;
        if (amount <= 0) return result(Status.INVALID_AMOUNT, clan);
        if (amount > ClanCurrency.MAX_BALANCE - clan.minhHuy()) return result(Status.LIMIT, clan);
        return change(clan, amount, HistoryType.MINHHUY_ADD, amount, reason, by, silent);
    }

    /** Removes Minh Huy; {@code NOT_ENOUGH} and nothing removed when the balance is lower. */
    public CurrencyResult take(ClanData clan, long amount, String reason, String by, boolean silent) {
        if (clan == null) return CurrencyResult.NO_CLAN;
        if (amount <= 0) return result(Status.INVALID_AMOUNT, clan);
        if (amount > clan.minhHuy()) return result(Status.NOT_ENOUGH, clan);
        return change(clan, -amount, HistoryType.MINHHUY_TAKE, amount, reason, by, silent);
    }

    /** Sets the balance (staff); the clan is not told. */
    public CurrencyResult set(ClanData clan, long value, String reason, String by) {
        if (clan == null) return CurrencyResult.NO_CLAN;
        if (value < 0) return result(Status.INVALID_AMOUNT, clan);
        if (value > ClanCurrency.MAX_BALANCE) return result(Status.LIMIT, clan);
        return change(clan, value - clan.minhHuy(), HistoryType.MINHHUY_SET, value, reason, by, true);
    }

    /** Back to 0 (staff); the clan is not told. A clan already at 0 is left alone (no history entry). */
    public CurrencyResult reset(ClanData clan, String by) {
        if (clan == null) return CurrencyResult.NO_CLAN;
        if (clan.minhHuy() == 0) return result(Status.OK, clan);
        return change(clan, -clan.minhHuy(), HistoryType.MINHHUY_RESET, clan.minhHuy(), "", by, true);
    }

    /**
     * Spends Minh Huy for the shop (GĐ4): like {@link #take}, but the history entry is written by the caller (the
     * purchase) and the clan is told by the caller too.
     */
    public CurrencyResult spend(ClanData clan, long amount, String reason) {
        if (clan == null) return CurrencyResult.NO_CLAN;
        if (amount <= 0) return result(Status.INVALID_AMOUNT, clan);
        if (amount > clan.minhHuy()) return result(Status.NOT_ENOUGH, clan);
        apply(clan, -amount, reason);
        return new CurrencyResult(Status.OK, -amount, clan.minhHuy());
    }

    private CurrencyResult change(ClanData clan, long delta, HistoryType type, long recorded, String reason, String by,
                                  boolean silent) {
        String why = reason(reason, "");
        apply(clan, delta, why);
        manager.history().record(clan.uuid(), type, null, by, null, recorded, why);
        if (!silent && delta != 0 && announcer != null) announcer.changed(clan, delta, clan.minhHuy(), why);
        return new CurrencyResult(Status.OK, delta, clan.minhHuy());
    }

    private void apply(ClanData clan, long delta, String reason) {
        clan.minhHuy(clan.minhHuy() + delta);
        db.saveMinhHuy(clan.uuid(), clan.minhHuy());
        ClanSnapshot snap = manager.publish(clan);
        if (delta != 0) events.call(new ClanMinhHuyChangeEvent(snap, delta, clan.minhHuy(), reason));
    }

    private static CurrencyResult result(Status status, ClanData clan) {
        return new CurrencyResult(status, 0, clan.minhHuy());
    }

    /** The clan of a player, {@code null} if none. */
    public ClanData clanOf(UUID player) {
        return player == null ? null : manager.of(player);
    }

    public ClanData clan(UUID clanUuid) {
        return clanUuid == null ? null : manager.clan(clanUuid);
    }
}
