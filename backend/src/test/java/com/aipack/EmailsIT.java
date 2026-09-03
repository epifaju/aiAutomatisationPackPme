package com.aipack;

import static org.assertj.core.api.Assertions.assertThat;

import com.aipack.ai.StubAIProvider;
import com.aipack.email.CapturingOutboundMailSender;
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
class EmailsIT {

    private static final UUID DEMO_COMPANY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID SEED_EMAIL = UUID.fromString("eeeeeeee-0000-4000-8000-000000000001");

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
    void listRequiresAccessToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/emails", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listAndGetAreScopedToCompany() throws Exception {
        String access = login();
        JsonNode list = getJson(access, "/api/v1/emails?size=50");
        assertThat(list.path("data").path("totalElements").asInt()).isGreaterThanOrEqualTo(10);

        JsonNode fetched = getJson(access, "/api/v1/emails/" + SEED_EMAIL).path("data");
        assertThat(fetched.path("companyId").asText()).isEqualTo(DEMO_COMPANY.toString());
        assertThat(fetched.path("fromAddress").asText()).contains("@demo.aipack.example");
        assertThat(fetched.path("analysis").path("category").asText()).isNotBlank();
        assertThat(fetched.path("autoSendEnabled").asBoolean()).isFalse();
    }

    @Test
    void doesNotLeakEmailsFromOtherCompanies() throws Exception {
        UUID otherCompany = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000077");
        UUID otherEmail = UUID.fromString("eeeeeeee-0000-4000-8000-000000000077");
        jdbcTemplate.update(
                """
                INSERT INTO companies (id, name, country, timezone, created_at, updated_at, created_by)
                VALUES (?, 'Other Co', 'FR', 'Europe/Paris', now(), now(), 'test')
                ON CONFLICT (id) DO NOTHING
                """,
                otherCompany);
        jdbcTemplate.update(
                """
                INSERT INTO emails (
                    id, company_id, message_id, from_address, to_address, subject, body_text,
                    received_at, status, created_at, updated_at, created_by
                ) VALUES (
                    ?, ?, 'hidden-message@aipack.example', 'hidden@demo.aipack.example',
                    'inbox@other.example', 'Secret', 'corps', now(), 'RECEIVED', now(), now(), 'test'
                )
                ON CONFLICT (id) DO NOTHING
                """,
                otherEmail,
                otherCompany);

        String access = login();
        ResponseEntity<String> hidden = restTemplate.exchange(
                "/api/v1/emails/" + otherEmail, HttpMethod.GET, bearer(access), String.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode list = getJson(access, "/api/v1/emails?size=100").path("data");
        for (JsonNode item : list.path("content")) {
            assertThat(item.path("id").asText()).isNotEqualTo(otherEmail.toString());
            assertThat(item.path("companyId").asText()).isEqualTo(DEMO_COMPANY.toString());
        }
    }

    @Test
    void webhookRequiresSecretThenIngestsAnalyzesAndAudits() throws Exception {
        String messageId = "it-message-" + UUID.randomUUID() + "@aipack.example";
        Map<String, Object> payload = Map.of(
                "companyId", DEMO_COMPANY.toString(),
                "messageId", messageId,
                "fromAddress", "marie.dupont@demo.aipack.example",
                "toAddress", "inbox@demo.aipack.example",
                "subject", "Demande de devis automatisation",
                "bodyText", "Bonjour, pourriez-vous nous envoyer un devis pour automatiser nos relances ?");

        ResponseEntity<String> unauthorized = restTemplate.postForEntity(
                "/webhook/email/incoming", json(null, payload), String.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> created = restTemplate.postForEntity(
                "/webhook/email/incoming", webhook(payload), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode email = objectMapper.readTree(created.getBody()).path("data");
        assertThat(email.path("status").asText()).isEqualTo("ANALYZED");
        assertThat(email.path("analysis").path("category").asText()).isEqualTo("CLIENT");
        assertThat(email.path("analysis").path("priority").asText()).isEqualTo("HIGH");
        assertThat(email.path("analysis").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(email.path("analysis").path("approvalStatus").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(email.path("analysis").path("suggestedReply").asText()).contains("Merci");
        assertThat(email.path("autoSendEnabled").asBoolean()).isFalse();

        ResponseEntity<String> duplicate = restTemplate.postForEntity(
                "/webhook/email/incoming", webhook(payload), String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode duplicateBody = objectMapper.readTree(duplicate.getBody()).path("data");
        assertThat(duplicateBody.path("id").asText()).isEqualTo(email.path("id").asText());

        String access = login();
        String emailId = email.path("id").asText();
        JsonNode audit = getJson(access, "/api/v1/audit?entityType=EMAIL&entityId=" + emailId + "&size=20")
                .path("data");
        assertThat(audit.path("totalElements").asInt()).isGreaterThanOrEqualTo(2);
        boolean ingested = false;
        boolean analyzed = false;
        for (JsonNode item : audit.path("content")) {
            if ("INGESTED".equals(item.path("action").asText())
                    && "email-ingestion".equals(item.path("workflow").asText())) {
                ingested = true;
            }
            if ("ANALYZED".equals(item.path("action").asText())
                    && "email-ai-analysis".equals(item.path("workflow").asText())
                    && "SUCCESS".equals(item.path("status").asText())) {
                analyzed = true;
            }
        }
        assertThat(ingested).isTrue();
        assertThat(analyzed).isTrue();
    }

    @Test
    void analyzeMarksUnavailableWhenProviderFails() throws Exception {
        StubAIProvider.MODE.set("unavailable");
        String messageId = "it-unavailable-" + UUID.randomUUID() + "@aipack.example";
        Map<String, Object> payload = Map.of(
                "companyId", DEMO_COMPANY.toString(),
                "messageId", messageId,
                "fromAddress", "paul.martin@demo.aipack.example",
                "toAddress", "inbox@demo.aipack.example",
                "subject", "Test indisponibilité",
                "bodyText", "Corps",
                "analyze", false);

        ResponseEntity<String> created = restTemplate.postForEntity(
                "/webhook/email/incoming", webhook(payload), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = objectMapper.readTree(created.getBody()).path("data").path("id").asText();
        assertThat(objectMapper.readTree(created.getBody()).path("data").path("status").asText())
                .isEqualTo("RECEIVED");

        String access = login();
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/emails/" + id + "/analyze", HttpMethod.POST, bearer(access), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody()).path("data");
        assertThat(body.path("status").asText()).isEqualTo("ERROR");
        assertThat(body.path("analysis").isMissingNode() || body.path("analysis").isNull()).isTrue();
    }

    @Test
    void approveDoesNotSendUntilExplicitSend() throws Exception {
        JsonNode email = ingestAnalyzed();
        String id = email.path("id").asText();
        String access = login();

        ResponseEntity<String> tooEarly = restTemplate.exchange(
                "/api/v1/emails/" + id + "/send", HttpMethod.POST, bearer(access), String.class);
        assertThat(tooEarly.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> approved = restTemplate.exchange(
                "/api/v1/emails/" + id + "/approve", HttpMethod.POST, bearer(access), String.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode approvedBody = objectMapper.readTree(approved.getBody()).path("data");
        assertThat(approvedBody.path("analysis").path("approvalStatus").asText()).isEqualTo("APPROVED");
        assertThat(CapturingOutboundMailSender.SENT).isEmpty();

        ResponseEntity<String> sent = restTemplate.exchange(
                "/api/v1/emails/" + id + "/send", HttpMethod.POST, bearer(access), String.class);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode sentBody = objectMapper.readTree(sent.getBody()).path("data");
        assertThat(sentBody.path("analysis").path("approvalStatus").asText()).isEqualTo("EXECUTED");
        assertThat(CapturingOutboundMailSender.SENT).hasSize(1);
        assertThat(CapturingOutboundMailSender.SENT.get(0).to()).isEqualTo("marie.dupont@demo.aipack.example");
        assertThat(CapturingOutboundMailSender.SENT.get(0).from()).isEqualTo("inbox@demo.aipack.example");
        assertThat(CapturingOutboundMailSender.SENT.get(0).subject()).startsWith("Re:");

        JsonNode audit = getJson(access, "/api/v1/audit?entityType=EMAIL&entityId=" + id + "&action=SENT&size=10")
                .path("data");
        assertThat(audit.path("totalElements").asInt()).isGreaterThanOrEqualTo(1);

        ResponseEntity<String> again = restTemplate.exchange(
                "/api/v1/emails/" + id + "/send", HttpMethod.POST, bearer(access), String.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectBlocksSend() throws Exception {
        JsonNode email = ingestAnalyzed();
        String id = email.path("id").asText();
        String access = login();
        ResponseEntity<String> rejected = restTemplate.exchange(
                "/api/v1/emails/" + id + "/reject", HttpMethod.POST, bearer(access), String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(rejected.getBody()).path("data").path("analysis").path("approvalStatus").asText())
                .isEqualTo("REJECTED");

        ResponseEntity<String> send = restTemplate.exchange(
                "/api/v1/emails/" + id + "/send", HttpMethod.POST, bearer(access), String.class);
        assertThat(send.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(CapturingOutboundMailSender.SENT).isEmpty();
    }

    @Test
    void invalidListStatusIsRejected() throws Exception {
        String access = login();
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/emails?status=NOPE", HttpMethod.GET, bearer(access), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("error").path("code").asText()).isEqualTo("INVALID_STATUS");
    }

    private JsonNode ingestAnalyzed() throws Exception {
        Map<String, Object> payload = Map.of(
                "companyId", DEMO_COMPANY.toString(),
                "messageId", "it-approve-" + UUID.randomUUID() + "@aipack.example",
                "fromAddress", "marie.dupont@demo.aipack.example",
                "toAddress", "inbox@demo.aipack.example",
                "subject", "Demande de devis",
                "bodyText", "Pouvez-vous envoyer un devis ?");
        ResponseEntity<String> created = restTemplate.postForEntity(
                "/webhook/email/incoming", webhook(payload), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(created.getBody()).path("data");
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
