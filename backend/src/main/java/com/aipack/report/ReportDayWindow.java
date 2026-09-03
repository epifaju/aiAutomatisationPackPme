package com.aipack.report;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record ReportDayWindow(LocalDate date, Instant from, Instant to, ZoneId zone) {

    public static ReportDayWindow of(LocalDate date, String timezone) {
        ZoneId zone = zoneOf(timezone);
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        return new ReportDayWindow(date, from, to, zone);
    }

    public static LocalDate today(String timezone, Clock clock) {
        return LocalDate.now(clock.withZone(zoneOf(timezone)));
    }

    public static LocalDate yesterday(String timezone, Clock clock) {
        return today(timezone, clock).minusDays(1);
    }

    static ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? "Europe/Paris" : timezone);
        } catch (RuntimeException ex) {
            return ZoneId.of("Europe/Paris");
        }
    }
}
