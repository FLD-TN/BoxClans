package com.dex.boxclans.util;

import com.dex.boxclans.config.Messages;

import java.util.Locale;
import java.util.OptionalLong;

/** Durations: {@code 1h30m} parsing for commands, "1 giờ 30 phút" formatting with the words of messages.yml. */
public final class Durations {

    private Durations() {
    }

    /** Formats seconds with at most two units, e.g. {@code 5 phút 3 giây}. */
    public static String format(long seconds, Messages m) {
        return format(seconds, word(m, "time.day", "ngày"), word(m, "time.hour", "giờ"),
                word(m, "time.minute", "phút"), word(m, "time.second", "giây"));
    }

    private static String word(Messages m, String key, String fallback) {
        if (m == null) return fallback;
        String v = m.text(key);
        return v.isEmpty() ? fallback : v;
    }

    public static String format(long seconds, String day, String hour, String minute, String second) {
        long s = Math.max(0, seconds);
        long d = s / 86400;
        long h = s % 86400 / 3600;
        long min = s % 3600 / 60;
        long sec = s % 60;
        long[] values = {d, h, min, sec};
        String[] words = {day, hour, minute, second};
        int first = 0;
        while (first < 3 && values[first] == 0) first++;
        StringBuilder out = new StringBuilder().append(values[first]).append(' ').append(words[first]);
        if (first < 3 && values[first + 1] > 0) {
            out.append(' ').append(values[first + 1]).append(' ').append(words[first + 1]);
        }
        return out.toString();
    }

    /** Parses {@code 90}, {@code 30s}, {@code 10m}, {@code 2h}, {@code 1d}, {@code 1h30m}; empty when invalid. */
    public static OptionalLong parseSeconds(String text) {
        if (text == null || text.isBlank()) return OptionalLong.empty();
        String s = text.trim().toLowerCase(Locale.ROOT);
        long total = 0;
        long number = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                number = (number < 0 ? 0 : number) * 10 + (c - '0');
                if (number > 100_000_000L) return OptionalLong.empty();
                continue;
            }
            if (number < 0) return OptionalLong.empty();
            long unit = switch (c) {
                case 's' -> 1;
                case 'm' -> 60;
                case 'h' -> 3600;
                case 'd' -> 86400;
                default -> -1;
            };
            if (unit < 0) return OptionalLong.empty();
            total += number * unit;
            number = -1;
        }
        if (number >= 0) total += number;
        return total > 0 ? OptionalLong.of(total) : OptionalLong.empty();
    }
}
