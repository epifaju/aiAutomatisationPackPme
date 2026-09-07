package com.aipack.email.dto;

import java.util.UUID;

public record EmailAttachmentResponse(
        UUID id, String originalFilename, String contentType, long sizeBytes, String checksumSha256) {}
