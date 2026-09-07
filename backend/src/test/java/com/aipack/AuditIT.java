package com.aipack;

import static org.assertj.core.api.Assertions.assertThat;

import com.aipack.audit.AuditRecord;
import com.aipack.audit.AuditService;
import com.aipack.audit.AuditStatus;
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
class AuditIT {

    private static final UUID DEMO_COMPANY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditService auditService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listRequiresAccessToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/audit", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listReturnsSeedAndLoginAuditForCurrentCompany() throws Exception {
        String access = login();
        JsonNode data = getAudit(access, "?size=100");
        assertThat(data.path("totalElements").asInt()).isGreaterThanOrEqualTo(21);
        assertThat(data.path("content").isArray()).isTrue();
        assertThat(data.path("content").get(0).path("timestamp").asText()).isNotBlank();

        boolean sawLogin = false;
        for (JsonNode item : data.path("content")) {
            assertThat(item.path("companyId").asText()).isEqualTo(DEMO_COMPANY.toString());
            if ("LOGIN".equals(item.path("action").asText()) && "USER".equals(item.path("entityType").asText())) {
                sawLogin = true;
            }
        }
        assertThat(sawLogin).isTrue();
    }

    @Test
    void listFiltersByWorkflowAndStatus() throws Exception {
        String access = login();
        JsonNode reminders = getAudit(access, "?workflow=invoice-reminder");
        assertThat(reminders.path("content")).isNotEmpty();
        for (JsonNode item : reminders.path("content")) {
            assertThat(item.path("workflow").asText()).isEqualTo("invoice-reminder");
        }

        JsonNode errors = getAudit(access, "?status=ERROR");
        assertThat(errors.path("totalElements").asInt()).isGreaterThanOrEqualTo(2);
        for (JsonNode item : errors.path("content")) {
            assertThat(item.path("status").asText()).isEqualTo("ERROR");
        }
    }

    @Test
    void invalidStatusIsRejected() throws Exception {
        String access = login();
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/audit?status=NOPE", HttpMethod.GET, bearer(access), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("INVALID_STATUS");
    }

    @Test
    void n8nErrorWebhookRequiresSecretThenWritesErrorAudit() throws Exception {
        Map<String, Object> payload = Map.of(
                "companyId",
                DEMO_COMPANY.toString(),
                "workflow",
                "[AIPACK][LEAD] Capture",
                "execution",
                "exec-wf091-it-001",
                "errorType",
                "NodeApiError",
                "errorMessage",
                "Backend returned 500",
                "metadata",
                Map.of("lastNode", "Backend", "secret", "should-redact", "accessToken", "abc"));

        ResponseEntity<String> unauthorized =
                restTemplate.postForEntity("/webhook/audit/n8n-error", jsonObject(payload), String.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> created =
                restTemplate.postForEntity("/webhook/audit/n8n-error", webhook(payload), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode data = objectMapper.readTree(created.getBody()).path("data");
        assertThat(data.path("action").asText()).isEqualTo("N8N_WORKFLOW_ERROR");
        assertThat(data.path("entityType").asText()).isEqualTo("WORKFLOW");
        assertThat(data.path("entityId").asText()).isEqualTo("exec-wf091-it-001");
        assertThat(data.path("status").asText()).isEqualTo("ERROR");
        assertThat(data.path("workflow").asText()).isEqualTo("[AIPACK][LEAD] Capture");
        assertThat(data.path("metadata").path("secret").asText()).isEqualTo("[REDACTED]");
        assertThat(data.path("metadata").path("accessToken").asText()).isEqualTo("[REDACTED]");
        assertThat(data.path("metadata").path("errorType").asText()).isEqualTo("NodeApiError");

        String access = login();
        JsonNode listed = getAudit(access, "?action=N8N_WORKFLOW_ERROR&entityId=exec-wf091-it-001&size=5");
        assertThat(listed.path("totalElements").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void recordStripsSecretsAndDoesNotLeakOtherCompanies() throws Exception {
        UUID otherCompany = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000099");
        jdbcTemplate.update(
                """
                INSERT INTO companies (id, name, country, timezone, created_at, updated_at, created_by)
                VALUES (?, 'Other Co', 'FR', 'Europe/Paris', now(), now(), 'test')
                ON CONFLICT (id) DO NOTHING
                """,
                otherCompany);
        jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    id, company_id, workflow, action, entity_type, entity_id, status, metadata, created_at, updated_at, created_by
                ) VALUES (
                    'a0d17999-0000-4000-8000-000000000099', ?, 'hidden', 'HIDDEN', 'LEAD', 'x', 'SUCCESS',
                    '{}'::jsonb, now(), now(), 'test'
                )
                ON CONFLICT (id) DO NOTHING
                """,
                otherCompany);

        auditService.record(new AuditRecord(
                DEMO_COMPANY,
                "invoice-reminder",
                "EMAIL_SENT",
                "INVOICE",
                "123",
                AuditStatus.SUCCESS,
                Map.of("password", "secret-pass", "invoiceNumber", "DEMO-1")));

        String access = login();
        JsonNode match = getAudit(access, "?action=EMAIL_SENT&entityType=INVOICE&entityId=123");
        assertThat(match.path("totalElements").asInt()).isEqualTo(1);
        JsonNode item = match.path("content").get(0);
        assertThat(item.path("workflow").asText()).isEqualTo("invoice-reminder");
        assertThat(item.path("metadata").path("password").asText()).isEqualTo("[REDACTED]");
        assertThat(item.path("metadata").path("invoiceNumber").asText()).isEqualTo("DEMO-1");

        JsonNode all = getAudit(access, "?size=100");
        for (JsonNode row : all.path("content")) {
            assertThat(row.path("id").asText()).isNotEqualTo("a0d17999-0000-4000-8000-000000000099");
            assertThat(row.path("companyId").asText()).isEqualTo(DEMO_COMPANY.toString());
        }
    }

    private String login() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                json(Map.of("email", "demo.admin@aipack.example", "password", "DemoAdmin!2026")),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).path("data").path("accessToken").asText();
    }

    private JsonNode getAudit(String access, String query) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/audit" + query, HttpMethod.GET, bearer(access), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("success").asBoolean()).isTrue();
        return body.path("data");
    }

    private HttpEntity<Map<String, String>> json(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Map<String, Object>> jsonObject(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(Map.copyOf(body), headers);
    }

    private HttpEntity<Map<String, Object>> webhook(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Webhook-Secret", "test-webhook-secret-16");
        return new HttpEntity<>(Map.copyOf(body), headers);
    }

    private HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
