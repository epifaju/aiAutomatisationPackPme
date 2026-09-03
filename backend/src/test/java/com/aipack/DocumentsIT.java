package com.aipack;

import static org.assertj.core.api.Assertions.assertThat;

import com.aipack.ai.StubAIProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class DocumentsIT {

    private static final UUID DEMO_COMPANY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID SEED_DOCUMENT = UUID.fromString("abcde000-0000-4000-8000-000000000001");
    private static final UUID SEED_REVIEW = UUID.fromString("abcde000-0000-4000-8000-000000000005");

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
    }

    @Test
    void listRequiresAccessToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/documents", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listAndGetAreScopedToCompany() throws Exception {
        String access = login();
        JsonNode list = getJson(access, "/api/v1/documents?size=50");
        assertThat(list.path("data").path("totalElements").asInt()).isGreaterThanOrEqualTo(5);

        JsonNode fetched = getJson(access, "/api/v1/documents/" + SEED_DOCUMENT).path("data");
        assertThat(fetched.path("companyId").asText()).isEqualTo(DEMO_COMPANY.toString());
        assertThat(fetched.path("originalFilename").asText()).startsWith("document-demo-");
        assertThat(fetched.path("extraction").path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void doesNotLeakDocumentsFromOtherCompanies() throws Exception {
        UUID otherCompany = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000088");
        UUID otherDocument = UUID.fromString("abcde000-0000-4000-8000-000000000088");
        jdbcTemplate.update(
                """
                INSERT INTO companies (id, name, country, timezone, created_at, updated_at, created_by)
                VALUES (?, 'Other Co', 'FR', 'Europe/Paris', now(), now(), 'test')
                ON CONFLICT (id) DO NOTHING
                """,
                otherCompany);
        jdbcTemplate.update(
                """
                INSERT INTO documents (
                    id, company_id, original_filename, content_type, storage_key, size_bytes,
                    document_type, status, checksum_sha256, created_at, updated_at, created_by
                ) VALUES (
                    ?, ?, 'secret.pdf', 'application/pdf', 'other/secret.pdf', 12,
                    'AUTRE', 'UPLOADED', repeat('b', 64), now(), now(), 'test'
                )
                ON CONFLICT (id) DO NOTHING
                """,
                otherDocument,
                otherCompany);

        String access = login();
        ResponseEntity<String> hidden = restTemplate.exchange(
                "/api/v1/documents/" + otherDocument, HttpMethod.GET, bearer(access), String.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode list = getJson(access, "/api/v1/documents?size=100").path("data");
        for (JsonNode item : list.path("content")) {
            assertThat(item.path("id").asText()).isNotEqualTo(otherDocument.toString());
            assertThat(item.path("companyId").asText()).isEqualTo(DEMO_COMPANY.toString());
        }
    }

    @Test
    void uploadProcessesWithAiAndAudits() throws Exception {
        String access = login();
        String filename = "facture-" + UUID.randomUUID() + ".txt";
        String body = "Facture F-2026-001 Fournisseur Démo 120 EUR TTC " + UUID.randomUUID();
        ResponseEntity<String> created = restTemplate.exchange(
                "/api/v1/documents", HttpMethod.POST, multipart(access, filename, body, true), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode document = objectMapper.readTree(created.getBody()).path("data");
        assertThat(document.path("status").asText()).isEqualTo("EXTRACTED");
        assertThat(document.path("documentType").asText()).isEqualTo("FACTURE");
        assertThat(document.path("contentType").asText()).isEqualTo("text/plain");
        assertThat(document.path("extraction").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(document.path("extraction").path("extractedJson").path("invoiceNumber").asText())
                .isEqualTo("F-2026-001");

        String id = document.path("id").asText();
        JsonNode audit = getJson(access, "/api/v1/audit?entityType=DOCUMENT&entityId=" + id + "&size=20")
                .path("data");
        assertThat(audit.path("totalElements").asInt()).isGreaterThanOrEqualTo(2);
        boolean uploaded = false;
        boolean extracted = false;
        for (JsonNode item : audit.path("content")) {
            if ("UPLOADED".equals(item.path("action").asText())
                    && "document-ingestion".equals(item.path("workflow").asText())) {
                uploaded = true;
            }
            if ("EXTRACTED".equals(item.path("action").asText())
                    && "document-ai-extraction".equals(item.path("workflow").asText())
                    && "SUCCESS".equals(item.path("status").asText())) {
                extracted = true;
            }
        }
        assertThat(uploaded).isTrue();
        assertThat(extracted).isTrue();

        ResponseEntity<String> duplicate = restTemplate.exchange(
                "/api/v1/documents", HttpMethod.POST, multipart(access, filename, body, true), String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(duplicate.getBody()).path("data").path("id").asText())
                .isEqualTo(id);
    }

    @Test
    void webhookRequiresSecretThenIngests() throws Exception {
        String content = "Devis maintenance " + UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "companyId", DEMO_COMPANY.toString(),
                "originalFilename", "devis.txt",
                "contentType", "text/plain",
                "contentBase64", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)),
                "documentType", "DEVIS");

        ResponseEntity<String> unauthorized =
                restTemplate.postForEntity("/webhook/documents/process", json(null, payload), String.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> created =
                restTemplate.postForEntity("/webhook/documents/process", webhook(payload), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode document = objectMapper.readTree(created.getBody()).path("data");
        assertThat(document.path("status").asText()).isEqualTo("EXTRACTED");
        assertThat(document.path("extraction").path("extractedJson").path("documentType").asText())
                .isEqualTo("FACTURE");
    }

    @Test
    void processMarksUnavailableWhenProviderFails() throws Exception {
        StubAIProvider.MODE.set("unavailable");
        String access = login();
        String filename = "unavailable-" + UUID.randomUUID() + ".txt";
        ResponseEntity<String> created = restTemplate.exchange(
                "/api/v1/documents",
                HttpMethod.POST,
                multipart(access, filename, "Texte " + UUID.randomUUID(), false),
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(objectMapper.readTree(created.getBody()).path("data").path("status").asText())
                .isEqualTo("UPLOADED");
        String id = objectMapper.readTree(created.getBody()).path("data").path("id").asText();

        ResponseEntity<String> processed = restTemplate.exchange(
                "/api/v1/documents/" + id + "/process", HttpMethod.POST, bearer(access), String.class);
        assertThat(processed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(processed.getBody()).path("data");
        assertThat(body.path("status").asText()).isEqualTo("ERROR");
        assertThat(body.path("extraction").path("status").asText()).isEqualTo("AI_UNAVAILABLE");
    }

    @Test
    void lowConfidenceRequiresHumanReview() throws Exception {
        StubAIProvider.MODE.set("low-confidence");
        String access = login();
        String filename = "review-" + UUID.randomUUID() + ".txt";
        ResponseEntity<String> created = restTemplate.exchange(
                "/api/v1/documents",
                HttpMethod.POST,
                multipart(access, filename, "Facture à relire " + UUID.randomUUID(), true),
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode document = objectMapper.readTree(created.getBody()).path("data");
        assertThat(document.path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(document.path("extraction").path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        String id = document.path("id").asText();

        ResponseEntity<String> approved = restTemplate.exchange(
                "/api/v1/documents/" + id + "/approve", HttpMethod.POST, bearer(access), String.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(approved.getBody()).path("data").path("status").asText())
                .isEqualTo("EXTRACTED");
        assertThat(objectMapper.readTree(approved.getBody()).path("data").path("extraction").path("status").asText())
                .isEqualTo("COMPLETED");
    }

    @Test
    void rejectMarksErrorAndBlocksApprove() throws Exception {
        String access = login();
        ResponseEntity<String> rejected = restTemplate.exchange(
                "/api/v1/documents/" + SEED_REVIEW + "/reject", HttpMethod.POST, bearer(access), String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(rejected.getBody()).path("data").path("status").asText())
                .isEqualTo("ERROR");

        ResponseEntity<String> approve = restTemplate.exchange(
                "/api/v1/documents/" + SEED_REVIEW + "/approve", HttpMethod.POST, bearer(access), String.class);
        assertThat(approve.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unsupportedMimeIsRejected() throws Exception {
        String access = login();
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/documents",
                HttpMethod.POST,
                multipart(access, "malware.exe", "MZ-not-an-image", false),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("error").path("code").asText()).isEqualTo("UNSUPPORTED_TYPE");
    }

    @Test
    void invalidListStatusIsRejected() throws Exception {
        String access = login();
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/documents?status=NOPE", HttpMethod.GET, bearer(access), String.class);
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

    private HttpEntity<MultiValueMap<String, Object>> multipart(
            String accessToken, String filename, String content, boolean process) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        body.add("process", Boolean.toString(process));
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
