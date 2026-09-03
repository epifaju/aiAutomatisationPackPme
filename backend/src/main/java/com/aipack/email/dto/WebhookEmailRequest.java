package com.aipack.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record WebhookEmailRequest(
        @NotNull UUID companyId,
        @Size(max = 998) String messageId,
        @NotBlank @Email @Size(max = 320) String fromAddress,
        @NotBlank @Email @Size(max = 320) String toAddress,
        @Size(max = 998) String subject,
        @Size(max = 100000) String bodyText,
        Instant receivedAt,
        Boolean analyze) {}
