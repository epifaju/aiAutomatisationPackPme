package com.aipack.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataSanitizerTest {

    @Test
    void redactsPasswordApiKeyTokensAndSecret() {
        Map<String, Object> sanitized = MetadataSanitizer.sanitize(Map.of(
                "password", "DemoAdmin!2026",
                "apiKey", "sk-live-123",
                "access_token", "tok",
                "refreshToken", "ref",
                "secret", "shh",
                "workflow", "invoice-reminder"));

        assertThat(sanitized)
                .containsEntry("password", MetadataSanitizer.REDACTED)
                .containsEntry("apiKey", MetadataSanitizer.REDACTED)
                .containsEntry("access_token", MetadataSanitizer.REDACTED)
                .containsEntry("refreshToken", MetadataSanitizer.REDACTED)
                .containsEntry("secret", MetadataSanitizer.REDACTED)
                .containsEntry("workflow", "invoice-reminder");
    }

    @Test
    void redactsNestedMapsAndJwtLikeValues() {
        Map<String, Object> sanitized = MetadataSanitizer.sanitize(Map.of(
                "nested", Map.of("client_secret", "hidden", "ok", true),
                "items", List.of(Map.of("authorization", "Bearer abc")),
                "note", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.signature"));

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) sanitized.get("nested");
        assertThat(nested)
                .containsEntry("client_secret", MetadataSanitizer.REDACTED)
                .containsEntry("ok", true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) sanitized.get("items");
        assertThat(items.getFirst()).containsEntry("authorization", MetadataSanitizer.REDACTED);

        assertThat(sanitized.get("note")).isEqualTo(MetadataSanitizer.REDACTED);
    }

    @Test
    void emptyMetadataBecomesEmptyMap() {
        assertThat(MetadataSanitizer.sanitize(null)).isEmpty();
        assertThat(MetadataSanitizer.sanitize(Map.of())).isEmpty();
    }
}
