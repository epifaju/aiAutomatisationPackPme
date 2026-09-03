package com.aipack;

import static org.assertj.core.api.Assertions.assertThat;

import com.aipack.ai.StubAIProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
class LeadsIT {

    private static final UUID DEMO_COMPANY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID SEED_LEAD = UUID.fromString("cccccccc-0000-4000-8000-000000000001");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetStub() {
        StubAIProvider.reset();
    }

    @Test
    void listRequiresAccessToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/leads", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void crudCreatesUpdatesAndDeletesWithinCompany() throws Exception {
        String access = login();
        JsonNode list = getJson(access, "/api/v1/leads?size=50");
        assertThat(list.path("data").path("totalElements").asInt()).isGreaterThanOrEqualTo(10);

        ResponseEntity<String> created = restTemplate.exchange(
                "/api/v1/leads",
                HttpMethod.POST,
                json(
                        access,
                        Map.of(
                                "fullName", "Marie Dupont",
                                "email", "marie.dupont@demo.aipack.example",
                                "companyName", "Dupont SAS",
                                "source", "WEB_FORM")),
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createdBody = objectMapper.readTree(created.getBody()).path("data");
        String id = createdBody.path("id").asText();
        assertThat(createdBody.path("status").asText()).isEqualTo("NEW");
        assertThat(createdBody.path("source").asText()).isEqualTo("WEB_FORM");
        assertThat(createdBody.path("companyId").asText()).isEqualTo(DEMO_COMPANY.toString());
        assertThat(createdBody.path("events")).isNotEmpty();

        JsonNode fetched = getJson(access, "/api/v1/leads/" + id).path("data");
        assertThat(fetched.path("fullName").asText()).isEqualTo("Marie Dupont");

        ResponseEntity<String> updated = restTemplate.exchange(
                "/api/v1/leads/" + id,
                HttpMethod.PUT,
                json(access, Map.of("status", "CONTACTED", "phone", "+33600000000")),
                String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updatedBody = objectMapper.readTree(updated.getBody()).path("data");
        assertThat(updatedBody.path("status").asText()).isEqualTo("CONTACTED");
        assertThat(updatedBody.path("phone").asText()).isEqualTo("+33600000000");

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/v1/leads/" + id, HttpMethod.DELETE, bearer(access), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> missing = restTemplate.exchange(
                "/api/v1/leads/" + id, HttpMethod.GET, bearer(access), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void doesNotLeakLeadsFromOtherCompanies() throws Exception {
        UUID otherCompany = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000088");
        UUID otherLead = UUID.fromString("cccccccc-0000-4000-8000-000000000088");
        jdbcTemplate.update(
                """
                INSERT INTO companies (id, name, country, timezone, created_at, updated_at, created_by)
                VALUES (?, 'Other Co', 'FR', 'Europe/Paris', now(), now(), 'test')
                ON CONFLICT (id) DO NOTHING
                """,
                otherCompany);
        jdbcTemplate.update(
                """
                INSERT INTO leads (
                    id, company_id, source, status, email, full_name, score, created_at, updated_at, created_by
                ) VALUES (
                    ?, ?, 'API', 'NEW', 'hidden@demo.aipack.example', 'Hidden Lead', 0, now(), now(), 'test'
                )
                ON CONFLICT (id) DO NOTHING
                """,
                otherLead,
                otherCompany);

        String access = login();
        ResponseEntity<String> hidden = restTemplate.exchange(
                "/api/v1/leads/" + otherLead, HttpMethod.GET, bearer(access), String.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode list = getJson(access, "/api/v1/leads?size=100").path("data");
        for (JsonNode item : list.path("content")) {
            assertThat(item.path("id").asText()).isNotEqualTo(otherLead.toString());
            assertThat(item.path("companyId").asText()).isEqualTo(DEMO_COMPANY.toString());
        }
    }

    @Test
    void webhookRequiresSecretThenCreatesQualifiesAndAudits() throws Exception {
        Map<String, Object> payload = Map.of(
                "companyId", DEMO_COMPANY.toString(),
                "fullName", "Paul Martin",
                "email", "paul.martin@demo.aipack.example",
                "companyName", "Martin SARL",
                "summary", "Demande de devis automatisation relances");

        ResponseEntity<String> unauthorized = restTemplate.postForEntity(
                "/webhook/leads/create", json(null, payload), String.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> created = restTemplate.postForEntity(
                "/webhook/leads/create", webhook(payload), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode lead = objectMapper.readTree(created.getBody()).path("data");
        assertThat(lead.path("source").asText()).isEqualTo("WEBHOOK");
        assertThat(lead.path("status").asText()).isEqualTo("QUALIFIED");
        assertThat(lead.path("aiStatus").asText()).isEqualTo("COMPLETED");
        assertThat(lead.path("score").asInt()).isEqualTo(72);
        assertThat(lead.path("scoreBand").asText()).isEqualTo("intéressant");
        assertThat(lead.path("probableNeed").asText()).isEqualTo("Automatisation administrative");

        String access = login();
        String leadId = lead.path("id").asText();
        JsonNode audit = getJson(access, "/api/v1/audit?entityType=LEAD&entityId=" + leadId + "&size=20")
                .path("data");
        assertThat(audit.path("totalElements").asInt()).isGreaterThanOrEqualTo(2);
        boolean createdAudit = false;
        boolean qualifiedAudit = false;
        for (JsonNode item : audit.path("content")) {
            if ("CREATED".equals(item.path("action").asText())
                    && "lead-capture".equals(item.path("workflow").asText())) {
                createdAudit = true;
            }
            if ("QUALIFIED".equals(item.path("action").asText())
                    && "lead-qualification".equals(item.path("workflow").asText())
                    && "SUCCESS".equals(item.path("status").asText())) {
                qualifiedAudit = true;
            }
        }
        assertThat(createdAudit).isTrue();
        assertThat(qualifiedAudit).isTrue();
    }

    @Test
    void qualifyMarksUnavailableWhenProviderFails() throws Exception {
        StubAIProvider.MODE.set("unavailable");
        String access = login();
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/leads/" + SEED_LEAD + "/qualify", HttpMethod.POST, bearer(access), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody()).path("data");
        assertThat(body.path("aiStatus").asText()).isEqualTo("AI_UNAVAILABLE");
        assertThat(body.path("status").asText()).isNotEqualTo("QUALIFIED");
    }

    @Test
    void invalidListStatusIsRejected() throws Exception {
        String access = login();
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/leads?status=NOPE", HttpMethod.GET, bearer(access), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("error").path("code").asText()).isEqualTo("INVALID_STATUS");
    }

    private String login() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                json(null, Map.of("email", "demo.admin@aipack.example", "password", "DemoAdmin!2026")),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).path("data").path("accessToken").asText();
    }

    private JsonNode getJson(String access, String path) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.GET, bearer(access), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("success").asBoolean()).isTrue();
        return body;
    }

    private HttpEntity<Map<String, Object>> json(String accessToken, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
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
