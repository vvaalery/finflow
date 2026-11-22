package com.finance.internal.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PaymentStatus {
    public static final String PENDING = "PENDING", APPROVED = "APPROVED", REJECTED = "REJECTED",
            PAID = "PAID", CANCELLED = "CANCELLED";

    private static final Set<String> ALL = Set.of(PENDING, APPROVED, REJECTED, PAID, CANCELLED);

    private static final Map<String, Set<String>> PAYMENT = Map.of(
            PENDING, Set.of(APPROVED, REJECTED, PAID, CANCELLED),
            APPROVED, Set.of(PAID, CANCELLED),
            REJECTED, Set.of(PENDING),
            PAID, Set.of(),
            CANCELLED, Set.of());

    private static final Map<String, Set<String>> REQUEST = Map.of(
            PENDING, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(PAID),
            REJECTED, Set.of(),
            PAID, Set.of());

    private PaymentStatus() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Статус не указан");
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (!ALL.contains(s)) throw new IllegalArgumentException("Неизвестный статус: " + raw);
        return s;
    }

    public static String current(String stored) {
        if (stored == null || stored.isBlank()) return PENDING;
        String s = stored.trim().toUpperCase(Locale.ROOT);
        return ALL.contains(s) ? s : PENDING;
    }

    public static boolean isTerminal(String status) {
        return PAID.equals(status) || CANCELLED.equals(status);
    }

    public static void checkPaymentTransition(String from, String to) {
        check(PAYMENT, "платежа", from, to, true);
    }

    public static void checkRequestTransition(String from, String to) {
        check(REQUEST, "заявки", from, to, false);
    }

    private static void check(Map<String, Set<String>> table, String what, String from, String to, boolean allowSame) {
        if (allowSame && from.equals(to)) return;
        if (!table.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalArgumentException("Недопустимый переход статуса " + what + ": " + from + " → " + to);
        }
    }
}
