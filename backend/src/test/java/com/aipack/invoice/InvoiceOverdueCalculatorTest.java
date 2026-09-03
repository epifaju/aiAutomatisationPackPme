package com.aipack.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceOverdueCalculatorTest {

    @Test
    void pastDueAndDaysOverdue() {
        LocalDate today = LocalDate.of(2026, 9, 2);
        assertThat(InvoiceOverdueCalculator.isPastDue(LocalDate.of(2026, 8, 28), today)).isTrue();
        assertThat(InvoiceOverdueCalculator.daysOverdue(LocalDate.of(2026, 8, 28), today)).isEqualTo(5);
        assertThat(InvoiceOverdueCalculator.isPastDue(today, today)).isFalse();
        assertThat(InvoiceOverdueCalculator.daysOverdue(today, today)).isEqualTo(0);
    }

    @Test
    void levelsDueFollowsConfiguredDelays() {
        assertThat(InvoiceOverdueCalculator.levelsDue(2, new Integer[] {3, 7, 15, 30})).isEmpty();
        assertThat(InvoiceOverdueCalculator.levelsDue(3, new Integer[] {3, 7, 15, 30})).containsExactly(3);
        assertThat(InvoiceOverdueCalculator.levelsDue(16, new Integer[] {3, 7, 15, 30})).containsExactly(3, 7, 15);
        assertThat(InvoiceOverdueCalculator.levelsDue(30, new Integer[] {3, 7, 15, 30})).containsExactly(3, 7, 15, 30);
    }

    @Test
    void invalidConfiguredLevelsFallBackToDefaults() {
        assertThat(InvoiceOverdueCalculator.normalizeLevels(new Integer[] {5, 10})).containsExactly(3, 7, 15, 30);
        assertThat(InvoiceOverdueCalculator.normalizeLevels(new Integer[] {3, 7})).containsExactly(3, 7);
    }

    @Test
    void manualAlertIsJ30() {
        assertThat(InvoiceOverdueCalculator.isManualAlert(15)).isFalse();
        assertThat(InvoiceOverdueCalculator.isManualAlert(30)).isTrue();
    }

    @Test
    void rejectsUnknownLevel() {
        assertThatThrownBy(() -> InvoiceOverdueCalculator.requireAllowedLevel(5))
                .isInstanceOf(InvoiceException.class);
    }
}
