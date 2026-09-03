package com.aipack.invoice;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public final class InvoiceOverdueCalculator {

    static final List<Integer> DEFAULT_LEVELS = List.of(3, 7, 15, 30);
    static final List<Integer> ALLOWED_LEVELS = List.of(3, 7, 15, 30);

    private InvoiceOverdueCalculator() {}

    public static boolean isPastDue(LocalDate dueDate, LocalDate today) {
        return dueDate != null && today != null && dueDate.isBefore(today);
    }

    public static int daysOverdue(LocalDate dueDate, LocalDate today) {
        if (!isPastDue(dueDate, today)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(dueDate, today);
    }

    public static List<Integer> levelsDue(int daysOverdue, Integer[] configured) {
        List<Integer> active = normalizeLevels(configured);
        return active.stream().filter(level -> daysOverdue >= level).toList();
    }

    public static List<Integer> normalizeLevels(Integer[] configured) {
        IntStream stream = configured == null ? IntStream.empty() : Arrays.stream(configured).mapToInt(Integer::intValue);
        List<Integer> filtered =
                stream.filter(level -> ALLOWED_LEVELS.contains(level)).distinct().sorted().boxed().toList();
        return filtered.isEmpty() ? DEFAULT_LEVELS : activeCopy(filtered);
    }

    public static boolean isManualAlert(int level) {
        return level >= 30;
    }

    public static int requireAllowedLevel(int level) {
        if (!ALLOWED_LEVELS.contains(level)) {
            throw InvoiceException.invalidReminderLevel();
        }
        return level;
    }

    private static List<Integer> activeCopy(List<Integer> levels) {
        return List.copyOf(levels);
    }
}
