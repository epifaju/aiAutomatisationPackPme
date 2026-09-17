package com.aipack;

import static org.assertj.core.api.Assertions.assertThat;

import com.aipack.config.WebhookSecretHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class WebhookTenantIT {

    private static final UUID DEMO_COMPANY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID OTHER_COMPANY = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
    private static final String DEMO_SECRET = "test-webhook-secret-16";
    private static final String OTHER_SECRET = "other-company-webhook-16";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void globalDemoSecretCannotWriteAnotherCompany() {
        ensureOtherCompany();

        ResponseEntity<String> spoofed = restTemplate.postForEntity(
                "/webhook/leads/create",
                webhook(
                        DEMO_SECRET,
                        Map.of(
                                "companyId", OTHER_COMPANY.toString(),
                                "fullName", "Eve",
                                "email", "eve@other.aipack.example",
                                "qualify", false)),
                String.class);
        assertThat(spoofed.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode(spoofed)).isEqualTo("WEBHOOK_TENANT_MISMATCH");

        ResponseEntity<String> otherClaimsDemo = restTemplate.postForEntity(
                "/webhook/leads/create",
                webhook(
                        OTHER_SECRET,
                        Map.of(
                                "companyId", DEMO_COMPANY.toString(),
                                "fullName", "Mallory",
                                "email", "mallory@demo.aipack.example",
                                "qualify", false)),
                String.class);
        assertThat(otherClaimsDemo.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> otherOk = restTemplate.postForEntity(
                "/webhook/leads/create",
                webhook(
                        OTHER_SECRET,
                        Map.of(
                                "companyId", OTHER_COMPANY.toString(),
                                "fullName", "Paul Other",
                                "email", "paul.other@other.aipack.example",
                                "qualify", false)),
                String.class);
        assertThat(otherOk.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json(otherOk).path("data").path("companyId").asText()).isEqualTo(OTHER_COMPANY.toString());
    }

    @Test
    void rotateWebhookSecretInvalidatesPreviousSecret() throws Exception {
        String access = login();
        try {
            ResponseEntity<String> rotated = restTemplate.exchange(
                    "/api/v1/settings/webhook-secret/rotate",
                    HttpMethod.POST,
                    bearer(access),
                    String.class);
            assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
            String newSecret = json(rotated).path("data").path("webhookSecret").asText();
            assertThat(newSecret).hasSize(64);

            ResponseEntity<String> oldRejected = restTemplate.postForEntity(
                    "/webhook/leads/create",
                    webhook(
                            DEMO_SECRET,
                            Map.of(
                                    "companyId", DEMO_COMPANY.toString(),
                                    "fullName", "Old Secret",
                                    "qualify", false)),
                    String.class);
            assertThat(oldRejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            ResponseEntity<String> newAccepted = restTemplate.postForEntity(
                    "/webhook/leads/create",
                    webhook(
                            newSecret,
                            Map.of(
                                    "companyId", DEMO_COMPANY.toString(),
                                    "fullName", "New Secret",
                                    "qualify", false)),
                    String.class);
            assertThat(newAccepted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        } finally {
            jdbcTemplate.update(
                    "UPDATE companies SET webhook_secret_hash = ? WHERE id = ?::uuid",
                    WebhookSecretHasher.sha256Hex(DEMO_SECRET),
                    DEMO_COMPANY.toString());
        }
    }

    private void ensureOtherCompany() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM companies WHERE id = ?::uuid", Integer.class, OTHER_COMPANY.toString());
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO companies (id, name, country, timezone, webhook_secret_hash, created_at, updated_at)
                VALUES (?::uuid, 'Other SAS', 'FR', 'Europe/Paris', ?, now(), now())
                """,
                OTHER_COMPANY.toString(),
                WebhookSecretHasher.sha256Hex(OTHER_SECRET));
    }

    private String login() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                jsonBody(Map.of("email", "demo.admin@aipack.example", "password", "DemoAdmin!2026")),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).path("data").path("accessToken").asText();
    }

    private String errorCode(ResponseEntity<String> response) {
        return json(response).path("error").path("code").asText();
    }

    private JsonNode json(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private HttpEntity<Map<String, Object>> webhook(String secret, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Webhook-Secret", secret);
        return new HttpEntity<>(Map.copyOf(body), headers);
    }

    private HttpEntity<Map<String, String>> jsonBody(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
