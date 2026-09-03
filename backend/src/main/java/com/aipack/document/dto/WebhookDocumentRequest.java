package com.aipack.document.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record WebhookDocumentRequest(
        @NotNull UUID companyId,
        UUID documentId,
        @Size(max = 512) String originalFilename,
        @Size(max = 128) String contentType,
        @Size(max = 28_000_000) String contentBase64,
        String documentType,
        Boolean process) {}
