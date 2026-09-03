package com.aipack;

import static org.assertj.core.api.Assertions.assertThat;

import com.aipack.ai.StubAIProvider;
import com.aipack.email.CapturingOutboundMailSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
class ReportsIT {

    private static final UUID DEMO_COMPANY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate WEBHOOK_DATE = LocalDate.of(2026, 9, 2);

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
    void resetStubs() {
        StubAIProvider.reset();
        CapturingOutboundMailSender.reset();
    }

    @Test
    void dashboardAndReportsRequireAccessToken() {
        assertThat(restTemplate.getForEntity("/api/v1/dashboard/summary", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity("/api/v1/reports/daily", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void dashboardSummaryReturnsTodayMetrics() throws Exception {
        String access = login();
        JsonNode data = getJson(access, "/api/v1/dashboard/summary").path("data");
        assertThat(data.path("date").asText()).isNotBlank();
        assertThat(data.path("metrics").path("currency").asText()).isEqualTo("EUR");
        assertThat(data.path("metrics").path("emailsReceived").isNumber()).isTrue();
        assertThat(data.path("metrics").path("overdueInvoices").isNumber()).isTrue();
        assertThat(data.path("recentActivity").isArray()).isTrue();
        assertThat(data.path("recentActivity").size()).isLessThanOrEqualTo(10);
    }

    @Test
    void generateIsIdempotentForTheSameDateThenSendsToAdmin() throws Exception {
        String access = login();
        Map<String, Object> payload = Map.of("date", REPORT_DATE.toString());

        ResponseEntity<String> first =
                restTemplate.exchange("/api/v1/reports/daily", HttpMethod.POST, json(access, payload), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = objectMapper.readTree(first.getBody()).path("data");
        String id = created.path("id").asText();
        assertThat(created.path("companyId").asText()).isEqualTo(DEMO_COMPANY.toString());
        assertThat(created.path("reportDate").asText()).isEqualTo(REPORT_DATE.toString());
        assertThat(created.path("status").asText()).isEqualTo("GENERATED");
        assertThat(created.path("summary").asText()).contains("Journée d'activité");
        assertThat(created.path("metrics").path("currency").asText()).isEqualTo("EUR");

        ResponseEntity<String> again =
                restTemplate.exchange("/api/v1/reports/daily", HttpMethod.POST, json(access, payload), String.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(objectMapper.readTree(again.getBody()).path("data").path("id").asText()).isEqualTo(id);

        JsonNode listed = getJson(access, "/api/v1/reports/daily?date=" + REPORT_DATE + "&size=10").path("data");
        assertThat(listed.path("totalElements").asInt()).isEqualTo(1);

        ResponseEntity<String> sent =
                restTemplate.exchange("/api/v1/reports/daily/" + id + "/send", HttpMethod.POST, bearer(access), String.class);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode afterSend = objectMapper.readTree(sent.getBody()).path("data");
        assertThat(afterSend.path("status").asText()).isEqualTo("SENT");
        assertThat(afterSend.path("sentAt").asText()).isNotBlank();
        assertThat(CapturingOutboundMailSender.SENT).hasSize(1);
        assertThat(CapturingOutboundMailSender.SENT.get(0).to()).isEqualTo("demo.admin@aipack.example");
        assertThat(CapturingOutboundMailSender.SENT.get(0).from()).isEqualTo("rapports@demo.aipack.example");
        assertThat(CapturingOutboundMailSender.SENT.get(0).subject()).contains(REPORT_DATE.toString());

        JsonNode audit = getJson(access, "/api/v1/audit?entityType=REPORT&entityId=" + id + "&action=EMAIL_SENT&size=10")
                .path("data");
        assertThat(audit.path("totalElements").asInt()).isGreaterThanOrEqualTo(1);

        ResponseEntity<String> regenerate =
                restTemplate.exchange("/api/v1/reports/daily", HttpMethod.POST, json(access, payload), String.class);
        assertThat(objectMapper.readTree(regenerate.getBody()).path("data").path("status").asText()).isEqualTo("SENT");
    }

    @Test
    void webhookRequiresSecretThenGenerates() throws Exception {
        Map<String, Object> payload = Map.of("companyId", DEMO_COMPANY.toString(), "date", WEBHOOK_DATE.toString());
        ResponseEntity<String> unauthorized =
                restTemplate.postForEntity("/webhook/reports/daily", json(null, payload), String.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> ok = restTemplate.postForEntity("/webhook/reports/daily", webhook(payload), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode data = objectMapper.readTree(ok.getBody()).path("data");
        assertThat(data.path("reportDate").asText()).isEqualTo(WEBHOOK_DATE.toString());
        assertThat(data.path("status").asText()).isEqualTo("GENERATED");
        assertThat(CapturingOutboundMailSender.SENT).isEmpty();
    }

    @Test
    void doesNotLeakReportsFromOtherCompanies() throws Exception {
        UUID otherCompany = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000099");
        UUID otherReport = UUID.fromString("bbbb0000-0000-4000-8000-000000000099");
        jdbcTemplate.update(
                """
                INSERT INTO companies (id, name, country, timezone, created_at, updated_at, created_by)
                VALUES (?, 'Other Co', 'FR', 'Europe/Paris', now(), now(), 'test')
                ON CONFLICT (id) DO NOTHING
                """,
                otherCompany);
        jdbcTemplate.update(
                """
                INSERT INTO daily_reports (
                    id, company_id, report_date, metrics, summary, status,
                    created_at, updated_at, created_by
                ) VALUES (
                    ?, ?, DATE '2026-01-01', '{}'::jsonb, 'hidden', 'GENERATED', now(), now(), 'test'
                )
                ON CONFLICT (id) DO NOTHING
                """,
                otherReport,
                otherCompany);

        String access = login();
        ResponseEntity<String> hidden = restTemplate.exchange(
                "/api/v1/reports/daily/" + otherReport, HttpMethod.GET, bearer(access), String.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> sendHidden = restTemplate.exchange(
                "/api/v1/reports/daily/" + otherReport + "/send", HttpMethod.POST, bearer(access), String.class);
        assertThat(sendHidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
