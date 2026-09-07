package com.aipack;

import static org.assertj.core.api.Assertions.assertThat;

import com.aipack.ratelimit.RateLimitFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "app.rate-limit.enabled=true",
            "app.rate-limit.auth.capacity=3",
            "app.rate-limit.auth.refill-period=1h",
            "app.rate-limit.webhook.capacity=3",
            "app.rate-limit.webhook.refill-period=1h"
        })
class RateLimitIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void resetBuckets() {
        rateLimitFilter.clearBuckets();
    }

    @Test
    void loginReturns429AfterAuthBudgetExhausted() throws Exception {
        Map<String, String> body = Map.of(
                "email", "demo.admin@aipack.example",
                "password", "wrong-password-for-rate-limit");

        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/login", json(body), String.class);
            assertThat(response.getStatusCode().value()).isLessThan(429);
        }

        ResponseEntity<String> blocked =
                restTemplate.postForEntity("/api/v1/auth/login", json(body), String.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getHeaders().getFirst("Retry-After")).isNotBlank();
        JsonNode payload = objectMapper.readTree(blocked.getBody());
        assertThat(payload.path("success").asBoolean()).isFalse();
        assertThat(payload.path("error").path("code").asText()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void webhookReturns429AfterWebhookBudgetExhausted() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Webhook-Secret", "not-the-real-secret");
        HttpEntity<String> entity = new HttpEntity<>("{}", headers);

        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> response =
                    restTemplate.postForEntity("/webhook/leads/create", entity, String.class);
            assertThat(response.getStatusCode().value()).isLessThan(429);
        }

        ResponseEntity<String> blocked =
                restTemplate.postForEntity("/webhook/leads/create", entity, String.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        JsonNode payload = objectMapper.readTree(blocked.getBody());
        assertThat(payload.path("error").path("code").asText()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    private HttpEntity<Map<String, String>> json(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
