package com.aipack.lead;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LeadScoreBandsTest {

    @Test
    void mapsConfigurableBands() {
        assertThat(LeadScoreBands.label(0, 30, 60, 80)).isEqualTo("faible");
        assertThat(LeadScoreBands.label(30, 30, 60, 80)).isEqualTo("faible");
        assertThat(LeadScoreBands.label(31, 30, 60, 80)).isEqualTo("moyen");
        assertThat(LeadScoreBands.label(61, 30, 60, 80)).isEqualTo("intéressant");
        assertThat(LeadScoreBands.label(81, 30, 60, 80)).isEqualTo("prioritaire");
        assertThat(LeadScoreBands.label(100, 30, 60, 80)).isEqualTo("prioritaire");
    }
}
