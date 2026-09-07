package com.aipack.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DemoDataSupportTest {

    @Test
    void autoEnablesOutsideProduction() {
        assertThat(DemoDataSupport.isEnabled("", "development")).isTrue();
        assertThat(DemoDataSupport.isEnabled(null, "dev")).isTrue();
        assertThat(DemoDataSupport.isEnabled(" ", "test")).isTrue();
    }

    @Test
    void autoDisablesInProduction() {
        assertThat(DemoDataSupport.isEnabled("", "production")).isFalse();
        assertThat(DemoDataSupport.isEnabled(null, "PROD")).isFalse();
    }

    @Test
    void explicitOverrideWins() {
        assertThat(DemoDataSupport.isEnabled("true", "production")).isTrue();
        assertThat(DemoDataSupport.isEnabled("false", "development")).isFalse();
    }

    @Test
    void explicitlyDisabledDetection() {
        assertThat(DemoDataSupport.isExplicitlyDisabled("false")).isTrue();
        assertThat(DemoDataSupport.isExplicitlyDisabled("true")).isFalse();
        assertThat(DemoDataSupport.isExplicitlyDisabled("")).isFalse();
        assertThat(DemoDataSupport.isExplicitlyDisabled(null)).isFalse();
    }
}
