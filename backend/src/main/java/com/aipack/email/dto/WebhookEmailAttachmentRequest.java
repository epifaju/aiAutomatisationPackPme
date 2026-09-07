package com.aipack.email.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebhookEmailAttachmentRequest(
        @NotBlank @Size(max = 512) String filename,
        @Size(max = 128) String contentType,
        @NotBlank @Size(max = 28_000_000) String contentBase64) {}
