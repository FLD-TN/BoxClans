package com.dex.boxclans.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Chat prompts (§4.1): the next chat line of a player is captured instead of reaching public chat. Used to type
 * a clan name (create, rename) or the name of a player to invite.
 *
 * <p>A wrong input keeps the prompt open (the handler reports the error and the player types again); a cancel
 * word or the timeout closes it. Opening and input handling run on the main thread; {@link #isOpen} is also
 * called from the async chat thread, hence the concurrent map.
 */
public final class ChatPrompt {

    /** What the prompt asks for. */
    public enum Type { CREATE, RENAME, INVITE, DESCRIPTION, SHOP }

    /** Why a prompt ended without completing. */
    public enum End { CANCELLED, TIMEOUT, REPLACED, QUIT }

    /** Result of one chat line. */
    public enum Result {
        /** The player has no open prompt: the line is normal chat. */
        NONE,
        /** The handler accepted the input; the prompt is closed. */
        DONE,
        /** The handler refused the input and reported why; the prompt stays open. */
        RETRY,
        /** A cancel word: the prompt is closed. */
        CANCELLED
    }

    /** Handles one line; returns true when finished (closes the prompt), false to ask again. */
    @FunctionalInterface
    public interface Handler {
        boolean accept(String input);
    }

    /** Called when a prompt ends without completing. */
    @FunctionalInterface
    public interface OnEnd {
        void ended(End reason);
    }

    private record Pending(Type type, Handler handler, OnEnd onEnd, long expiresAt) {}

    private final Map<UUID, Pending> open = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile long timeoutMillis;
    private volatile List<String> cancelWords;

    public ChatPrompt(LongSupplier clock, long timeoutSeconds, List<String> cancelWords) {
        this.clock = clock;
        configure(timeoutSeconds, cancelWords);
    }

    public void configure(long timeoutSeconds, List<String> cancelWords) {
        this.timeoutMillis = timeoutSeconds * 1000L;
        List<String> words = new ArrayList<>();
        for (String w : cancelWords) words.add(w.toLowerCase(Locale.ROOT).trim());
        this.cancelWords = List.copyOf(words);
    }

    public long timeoutSeconds() {
        return timeoutMillis / 1000L;
    }

    /** Opens a prompt; a prompt already open for the player ends with {@link End#REPLACED}. */
    public void open(UUID player, Type type, Handler handler, OnEnd onEnd) {
        Pending previous = open.put(player, new Pending(type, handler, onEnd, clock.getAsLong() + timeoutMillis));
        if (previous != null) previous.onEnd().ended(End.REPLACED);
    }

    /** Whether the player's next chat line belongs to a prompt (any thread). */
    public boolean isOpen(UUID player) {
        Pending p = open.get(player);
        return p != null && p.expiresAt() > clock.getAsLong();
    }

    public Type typeOf(UUID player) {
        Pending p = open.get(player);
        return p == null ? null : p.type();
    }

    /** Handles a chat line of a player (main thread). */
    public Result input(UUID player, String text) {
        Pending p = open.get(player);
        if (p == null) return Result.NONE;
        if (p.expiresAt() <= clock.getAsLong()) {
            expire();
            return Result.NONE;
        }
        String line = text == null ? "" : text.trim();
        if (cancelWords.contains(line.toLowerCase(Locale.ROOT))) {
            if (open.remove(player, p)) p.onEnd().ended(End.CANCELLED);
            return Result.CANCELLED;
        }
        boolean done = p.handler().accept(line);
        if (done) {
            open.remove(player, p);
            return Result.DONE;
        }
        return Result.RETRY;
    }

    /** Closes every expired prompt with {@link End#TIMEOUT} (main thread, called every second). */
    public void expire() {
        long now = clock.getAsLong();
        for (Map.Entry<UUID, Pending> e : open.entrySet()) {
            Pending p = e.getValue();
            if (p.expiresAt() <= now && open.remove(e.getKey(), p)) p.onEnd().ended(End.TIMEOUT);
        }
    }

    /** Closes a player's prompt (quit, or a menu action). */
    public void close(UUID player, End reason) {
        Pending p = open.remove(player);
        if (p != null) p.onEnd().ended(reason);
    }

    /** Closes a prompt without calling its end handler (it completed elsewhere). */
    public void discard(UUID player) {
        open.remove(player);
    }

    public void clear() {
        open.clear();
    }
}
