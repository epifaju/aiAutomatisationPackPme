package com.aipack.lead.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record WebhookLeadRequest(
        @NotNull UUID companyId,
        @NotBlank @Size(max = 255) String fullName,
        @Email @Size(max = 320) String email,
        @Size(max = 255) String companyName,
        @Size(max = 64) String phone,
        @Size(max = 8000) String summary,
        Boolean qualify) {}
