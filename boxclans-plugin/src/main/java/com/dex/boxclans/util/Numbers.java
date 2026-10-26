package com.dex.boxclans.util;

/** Number display shared by the menus and messages. */
public final class Numbers {

    private Numbers() {
    }

    /** Groups of three digits separated by a dot: {@code 1250} → {@code 1.250}, {@code -3000} → {@code -3.000}. */
    public static String grouped(long n) {
        String digits = Long.toString(Math.abs(n));
        StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3 + 1);
        int lead = digits.length() % 3 == 0 ? 3 : digits.length() % 3;
        out.append(digits, 0, lead);
        for (int i = lead; i < digits.length(); i += 3) out.append('.').append(digits, i, i + 3);
        return n < 0 ? "-" + out : out.toString();
    }

    /** A positive whole number written with or without dots ({@code 1.250}), -1 when invalid. */
    public static long parsePositive(String s) {
        if (s == null) return -1;
        String digits = s.trim().replace(".", "");
        if (digits.isEmpty() || digits.length() > 18) return -1;
        for (int i = 0; i < digits.length(); i++) if (!Character.isDigit(digits.charAt(i))) return -1;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
