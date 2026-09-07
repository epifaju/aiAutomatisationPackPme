package com.aipack.audit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookN8nErrorRequest(
        @NotNull UUID companyId,
        String workflow,
        String execution,
        String errorType,
        String errorMessage,
        Map<String, Object> metadata) {}
