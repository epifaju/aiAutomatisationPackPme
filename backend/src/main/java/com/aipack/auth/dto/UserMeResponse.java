package com.aipack.auth.dto;

import java.util.UUID;

public record UserMeResponse(
        UUID id, String email, String fullName, String role, UUID companyId, String companyName) {}
