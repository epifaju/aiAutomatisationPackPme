package com.aipack.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailServiceTest {

    @Test
    void replySubjectAddsPrefixOnce() {
        assertThat(EmailService.replySubject("Devis")).isEqualTo("Re: Devis");
        assertThat(EmailService.replySubject("Re: Devis")).isEqualTo("Re: Devis");
        assertThat(EmailService.replySubject("RE: Devis")).isEqualTo("RE: Devis");
        assertThat(EmailService.replySubject("  ")).isEqualTo("Re:");
        assertThat(EmailService.replySubject(null)).isEqualTo("Re:");
    }
}
