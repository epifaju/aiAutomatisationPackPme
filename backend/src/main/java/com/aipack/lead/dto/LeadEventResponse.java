package com.aipack.lead.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LeadEventResponse(UUID id, String eventType, Map<String, Object> payload, Instant occurredAt) {}
