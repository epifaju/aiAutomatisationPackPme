package com.aipack.virus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClamAvSupportTest {

    @Test
    void autoEnablesFailClosedInProduction() {
        assertThat(ClamAvSupport.isEnabled("", "production")).isTrue();
        assertThat(ClamAvSupport.isEnabled("auto", "prod")).isTrue();
        assertThat(ClamAvSupport.isFailOpen("", "production")).isFalse();
        assertThat(ClamAvSupport.isFailOpen("auto", "prod")).isFalse();
    }

    @Test
    void autoDisablesFailOpenOutsideProduction() {
        assertThat(ClamAvSupport.isEnabled("", "development")).isFalse();
        assertThat(ClamAvSupport.isFailOpen("", "development")).isTrue();
    }

    @Test
    void explicitFlagsWin() {
        assertThat(ClamAvSupport.isEnabled("true", "development")).isTrue();
        assertThat(ClamAvSupport.isEnabled("false", "production")).isFalse();
        assertThat(ClamAvSupport.isFailOpen("true", "production")).isTrue();
        assertThat(ClamAvSupport.isFailOpen("false", "development")).isFalse();
    }
}
