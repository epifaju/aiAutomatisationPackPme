package com.aipack.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ReportDayWindowTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Test
    void windowIsStartOfDayInclusiveToNextMidnightExclusive() {
        ReportDayWindow window = ReportDayWindow.of(LocalDate.of(2026, 9, 2), "Europe/Paris");
        assertThat(window.date()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 9, 2).atStartOfDay(PARIS).toInstant());
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 9, 3).atStartOfDay(PARIS).toInstant());
        assertThat(window.zone()).isEqualTo(PARIS);
    }

    @Test
    void invalidTimezoneFallsBackToParis() {
        ReportDayWindow window = ReportDayWindow.of(LocalDate.of(2026, 1, 1), "Not/AZone");
        assertThat(window.zone()).isEqualTo(PARIS);
        ReportDayWindow blank = ReportDayWindow.of(LocalDate.of(2026, 1, 1), "  ");
        assertThat(blank.zone()).isEqualTo(PARIS);
    }

    @Test
    void todayAndYesterdayUseCompanyZone() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-02T22:30:00Z"), ZoneId.of("UTC"));
        assertThat(ReportDayWindow.today("Europe/Paris", clock)).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(ReportDayWindow.yesterday("Europe/Paris", clock)).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(ReportDayWindow.today("UTC", clock)).isEqualTo(LocalDate.of(2026, 9, 2));
    }
}
