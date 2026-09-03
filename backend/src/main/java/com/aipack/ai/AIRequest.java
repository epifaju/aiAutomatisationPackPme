package com.aipack.ai;

import java.util.UUID;

public record AIRequest(UUID companyId, String purpose, String prompt) {}
