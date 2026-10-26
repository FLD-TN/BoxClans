package com.dex.boxclans.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPromptTest {

    private final AtomicLong clock = new AtomicLong(10_000);
    private final ChatPrompt prompts = new ChatPrompt(clock::get, 60, List.of("cancel", "huỷ"));
    private final UUID player = UUID.randomUUID();
    private final List<String> accepted = new ArrayList<>();
    private final List<ChatPrompt.End> ended = new ArrayList<>();

    private void openNameprompt() {
        prompts.open(player, ChatPrompt.Type.CREATE, input -> {
            if (input.length() < 3) return false; // wrong: the handler reports and waits
            accepted.add(input);
            return true;
        }, ended::add);
    }

    @Test
    void wrongInputWaitsForAnotherLine() {
        openNameprompt();
        assertTrue(prompts.isOpen(player));
        assertEquals(ChatPrompt.Result.RETRY, prompts.input(player, "ab"));
        assertTrue(prompts.isOpen(player), "still waiting");
        assertEquals(ChatPrompt.Result.RETRY, prompts.input(player, "x"));
        assertEquals(ChatPrompt.Result.DONE, prompts.input(player, "&cRồng Đỏ"));
        assertEquals(List.of("&cRồng Đỏ"), accepted);
        assertFalse(prompts.isOpen(player));
        assertTrue(ended.isEmpty());
        assertEquals(ChatPrompt.Result.NONE, prompts.input(player, "normal chat"));
    }

    @Test
    void cancelWordCloses() {
        openNameprompt();
        assertEquals(ChatPrompt.Result.CANCELLED, prompts.input(player, "  CANCEL "));
        assertFalse(prompts.isOpen(player));
        assertEquals(List.of(ChatPrompt.End.CANCELLED), ended);
        assertTrue(accepted.isEmpty());

        openNameprompt();
        assertEquals(ChatPrompt.Result.CANCELLED, prompts.input(player, "huỷ"));
    }

    @Test
    void timeoutCloses() {
        openNameprompt();
        clock.addAndGet(59_000);
        assertTrue(prompts.isOpen(player));
        prompts.expire();
        assertTrue(ended.isEmpty());
        clock.addAndGet(1_000);
        assertFalse(prompts.isOpen(player), "chat goes to public chat again once expired");
        prompts.expire();
        assertEquals(List.of(ChatPrompt.End.TIMEOUT), ended);
        assertEquals(ChatPrompt.Result.NONE, prompts.input(player, "&cRồng Đỏ"));
        assertTrue(accepted.isEmpty());
    }

    @Test
    void lateInputAfterTimeoutIsNotAccepted() {
        openNameprompt();
        clock.addAndGet(61_000);
        assertEquals(ChatPrompt.Result.NONE, prompts.input(player, "&cRồng Đỏ"));
        assertEquals(List.of(ChatPrompt.End.TIMEOUT), ended);
    }

    @Test
    void newPromptReplacesOldAndQuitCloses() {
        openNameprompt();
        prompts.open(player, ChatPrompt.Type.INVITE, input -> true, ended::add);
        assertEquals(List.of(ChatPrompt.End.REPLACED), ended);
        assertEquals(ChatPrompt.Type.INVITE, prompts.typeOf(player));
        prompts.close(player, ChatPrompt.End.QUIT);
        assertFalse(prompts.isOpen(player));
    }
}
