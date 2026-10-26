package com.dex.boxclans.history;

import com.dex.boxclans.storage.Database;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Writes clan history entries (BOXCLANS_SHOP_SPEC.md §6.1) to {@code clan_history}. Player names are resolved and
 * stored when the entry is written, so the history still shows them after the player left the clan or renamed.
 * Main thread; the write itself goes through the database queue.
 */
public final class ClanHistory {

    /** What {@link com.dex.boxclans.clan.ClanManager} and the services call; {@link #NONE} records nothing. */
    @FunctionalInterface
    public interface Recorder {
        Recorder NONE = (clan, type, actor, actorName, target, amount, detail) -> {
        };

        /**
         * @param actor     who did it, {@code null} for staff / console
         * @param actorName shown instead of the actor's name when not {@code null} (a command sender)
         * @param target    the other player, may be {@code null}
         */
        void record(UUID clan, HistoryType type, UUID actor, String actorName, UUID target, long amount, String detail);

        default void record(UUID clan, HistoryType type, UUID actor, UUID target) {
            record(clan, type, actor, null, target, 0, "");
        }
    }

    private final Database db;
    private final LongSupplier clock;
    private final Function<UUID, String> names;

    public ClanHistory(Database db, LongSupplier clock, Function<UUID, String> names) {
        this.db = db;
        this.clock = clock;
        this.names = names;
    }

    /** The recorder writing to the database. */
    public Recorder recorder() {
        return this::record;
    }

    private void record(UUID clan, HistoryType type, UUID actor, String actorName, UUID target, long amount,
                        String detail) {
        if (clan == null || type == null) return;
        String actorShown = actorName != null ? actorName : actor == null ? "" : name(actor);
        String targetShown = target == null ? "" : name(target);
        db.addHistory(new Database.HistoryRow(0, clan, clock.getAsLong(), type.key(), actor, actorShown, target,
                targetShown, amount, detail == null ? "" : detail));
    }

    private String name(UUID player) {
        try {
            String n = names.apply(player);
            return n == null ? "" : n;
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
