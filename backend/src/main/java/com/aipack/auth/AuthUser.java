package com.aipack.auth;

import java.util.UUID;

public record AuthUser(UUID id, UUID companyId, String email, String role) {}
