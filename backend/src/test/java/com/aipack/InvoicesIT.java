package com.aipack;

import static org.assertj.core.api.Assertions.assertThat;

import com.aipack.email.CapturingOutboundMailSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
class InvoicesIT {

    private static final UUID DEMO_COMPANY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID SEED_CUSTOMER = UUID.fromString("dddddddd-0000-4000-8000-000000000001");
    private static final UUID SEED_INVOICE = UUID.fromString("ffff0000-0000-4000-8000-000000000001");

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
        CapturingOutboundMailSender.reset();
    }

    @Test
    void listRequiresAccessToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/invoices", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> customers = restTemplate.getForEntity("/api/v1/customers", String.class);
        assertThat(customers.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listAndGetAreScopedToCompany() throws Exception {
        String access = login();
        JsonNode invoices = getJson(access, "/api/v1/invoices?size=50");
        assertThat(invoices.path("data").path("totalElements").asInt()).isGreaterThanOrEqualTo(10);

        JsonNode fetched = getJson(access, "/api/v1/invoices/" + SEED_INVOICE).path("data");
        assertThat(fetched.path("companyId").asText()).isEqualTo(DEMO_COMPANY.toString());
        assertThat(fetched.path("invoiceNumber").asText()).startsWith("DEMO-2026-");
        assertThat(fetched.path("customer").path("id").asText()).isNotBlank();

        JsonNode customers = getJson(access, "/api/v1/customers?size=20");
        assertThat(customers.path("data").path("totalElements").asInt()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void doesNotLeakInvoicesFromOtherCompanies() throws Exception {
        UUID otherCompany = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000099");
        UUID otherCustomer = UUID.fromString("dddddddd-0000-4000-8000-000000000099");
        UUID otherInvoice = UUID.fromString("ffff0000-0000-4000-8000-000000000099");
        jdbcTemplate.update(
                """
                INSERT INTO companies (id, name, country, timezone, created_at, updated_at, created_by)
                VALUES (?, 'Other Co', 'FR', 'Europe/Paris', now(), now(), 'test')
                ON CONFLICT (id) DO NOTHING
                """,
                otherCompany);
        jdbcTemplate.update(
                """
                INSERT INTO customers (id, company_id, name, email, created_at, updated_at, created_by)
                VALUES (?, ?, 'Hidden', 'hidden@other.example', now(), now(), 'test')
                ON CONFLICT (id) DO NOTHING
                """,
                otherCustomer,
                otherCompany);
        jdbcTemplate.update(
                """
                INSERT INTO invoices (
                    id, company_id, customer_id, invoice_number, invoice_date, due_date,
                    amount_excluding_tax, vat, amount_including_tax, currency, status,
                    created_at, updated_at, created_by
                ) VALUES (
                    ?, ?, ?, 'HIDDEN-1', DATE '2026-01-01', DATE '2026-01-15',
                    10, 2, 12, 'EUR', 'SENT', now(), now(), 'test'
                )
                ON CONFLICT (id) DO NOTHING
                """,
                otherInvoice,
                otherCompany,
                otherCustomer);

        String access = login();
        ResponseEntity<String> hiddenInvoice = restTemplate.exchange(
                "/api/v1/invoices/" + otherInvoice, HttpMethod.GET, bearer(access), String.class);
        assertThat(hiddenInvoice.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> hiddenCustomer = restTemplate.exchange(
                "/api/v1/customers/" + otherCustomer, HttpMethod.GET, bearer(access), String.class);
        assertThat(hiddenCustomer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void overdueDetectionCreatesIdempotentReminderThenApproveSendToMailpit() throws Exception {
        String access = login();
        String number = "IT-" + UUID.randomUUID();
        LocalDate due = LocalDate.now().minusDays(5);
        Map<String, Object> payload = invoicePayload(SEED_CUSTOMER, number, due, "SENT");
        ResponseEntity<String> created =
                restTemplate.exchange("/api/v1/invoices", HttpMethod.POST, json(access, payload), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = objectMapper.readTree(created.getBody()).path("data").path("id").asText();

        ResponseEntity<String> detected = restTemplate.exchange(
                "/api/v1/invoices/overdue/detect?invoiceId=" + id, HttpMethod.POST, bearer(access), String.class);
        assertThat(detected.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode detection = objectMapper.readTree(detected.getBody()).path("data");
        assertThat(detection.path("invoicesMarkedOverdue").asInt()).isEqualTo(1);
        assertThat(detection.path("remindersCreated").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode invoice = getJson(access, "/api/v1/invoices/" + id).path("data");
        assertThat(invoice.path("status").asText()).isEqualTo("OVERDUE");
        assertThat(invoice.path("daysOverdue").asInt()).isGreaterThanOrEqualTo(5);
        assertThat(invoice.path("autoSendEnabled").asBoolean()).isFalse();
        assertThat(invoice.path("reminders")).isNotEmpty();
        assertThat(invoice.path("reminders").get(0).path("reminderLevel").asInt()).isEqualTo(3);
        assertThat(invoice.path("reminders").get(0).path("status").asText()).isEqualTo("PENDING_APPROVAL");

        ResponseEntity<String> again = restTemplate.exchange(
                "/api/v1/invoices/overdue/detect?invoiceId=" + id, HttpMethod.POST, bearer(access), String.class);
        assertThat(objectMapper.readTree(again.getBody()).path("data").path("remindersCreated").asInt()).isEqualTo(0);

        ResponseEntity<String> tooEarly = restTemplate.exchange(
                "/api/v1/invoices/" + id + "/reminders/3/send", HttpMethod.POST, bearer(access), String.class);
        assertThat(tooEarly.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> approved = restTemplate.exchange(
                "/api/v1/invoices/" + id + "/reminders/3/approve", HttpMethod.POST, bearer(access), String.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(approved.getBody()).path("data").path("reminders").get(0).path("status").asText())
                .isEqualTo("APPROVED");
        assertThat(CapturingOutboundMailSender.SENT).isEmpty();

        ResponseEntity<String> sent = restTemplate.exchange(
                "/api/v1/invoices/" + id + "/reminders/3/send", HttpMethod.POST, bearer(access), String.class);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(sent.getBody()).path("data").path("reminders").get(0).path("status").asText())
                .isEqualTo("EXECUTED");
        assertThat(CapturingOutboundMailSender.SENT).hasSize(1);
        assertThat(CapturingOutboundMailSender.SENT.get(0).to()).isEqualTo("client.alpha@demo.aipack.example");
        assertThat(CapturingOutboundMailSender.SENT.get(0).from()).isEqualTo("relances@demo.aipack.example");
        assertThat(CapturingOutboundMailSender.SENT.get(0).subject()).contains(number);

        JsonNode audit = getJson(access, "/api/v1/audit?entityType=INVOICE&entityId=" + id + "&action=EMAIL_SENT&size=10")
                .path("data");
        assertThat(audit.path("totalElements").asInt()).isGreaterThanOrEqualTo(1);

        ResponseEntity<String> duplicateSend = restTemplate.exchange(
                "/api/v1/invoices/" + id + "/reminders/3/send", HttpMethod.POST, bearer(access), String.class);
        assertThat(duplicateSend.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void paidInvoiceNeverSendsReminder() throws Exception {
        String access = login();
        String number = "PAID-" + UUID.randomUUID();
        LocalDate due = LocalDate.now().minusDays(8);
        ResponseEntity<String> created = restTemplate.exchange(
                "/api/v1/invoices",
                HttpMethod.POST,
                json(access, invoicePayload(SEED_CUSTOMER, number, due, "SENT")),
                String.class);
        String id = objectMapper.readTree(created.getBody()).path("data").path("id").asText();
        restTemplate.exchange(
                "/api/v1/invoices/overdue/detect?invoiceId=" + id, HttpMethod.POST, bearer(access), String.class);
        restTemplate.exchange(
                "/api/v1/invoices/" + id + "/reminders/3/approve", HttpMethod.POST, bearer(access), String.class);

        Map<String, Object> paid = new LinkedHashMap<>();
        paid.put("status", "PAID");
        ResponseEntity<String> updated =
                restTemplate.exchange("/api/v1/invoices/" + id, HttpMethod.PUT, json(access, paid), String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> send = restTemplate.exchange(
                "/api/v1/invoices/" + id + "/reminders/3/send", HttpMethod.POST, bearer(access), String.class);
        assertThat(send.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(CapturingOutboundMailSender.SENT).isEmpty();
    }

    @Test
    void webhookRequiresSecretThenDetects() throws Exception {
        String access = login();
        String number = "WH-" + UUID.randomUUID();
        ResponseEntity<String> created = restTemplate.exchange(
                "/api/v1/invoices",
                HttpMethod.POST,
                json(access, invoicePayload(SEED_CUSTOMER, number, LocalDate.now().minusDays(4), "SENT")),
                String.class);
        String id = objectMapper.readTree(created.getBody()).path("data").path("id").asText();

        Map<String, Object> payload = Map.of("companyId", DEMO_COMPANY.toString(), "invoiceId", id);
        ResponseEntity<String> unauthorized =
                restTemplate.postForEntity("/webhook/invoices/reminder", json(null, payload), String.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> ok =
                restTemplate.postForEntity("/webhook/invoices/reminder", webhook(payload), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(ok.getBody()).path("data").path("invoicesMarkedOverdue").asInt()).isEqualTo(1);
    }

    @Test
    void cannotDeleteCustomerWithInvoices() throws Exception {
        String access = login();
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/customers/" + SEED_CUSTOMER, HttpMethod.DELETE, bearer(access), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void duplicateInvoiceNumberIsRejected() throws Exception {
        String access = login();
        String number = "DUP-" + UUID.randomUUID();
        LocalDate due = LocalDate.now().plusDays(10);
        ResponseEntity<String> first = restTemplate.exchange(
                "/api/v1/invoices",
                HttpMethod.POST,
                json(access, invoicePayload(SEED_CUSTOMER, number, due, "DRAFT")),
                String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> second = restTemplate.exchange(
                "/api/v1/invoices",
                HttpMethod.POST,
                json(access, invoicePayload(SEED_CUSTOMER, number, due, "DRAFT")),
                String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidListStatusIsRejected() throws Exception {
        String access = login();
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/invoices?status=NOPE", HttpMethod.GET, bearer(access), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).path("error").path("code").asText())
                .isEqualTo("INVALID_STATUS");
    }

    private Map<String, Object> invoicePayload(UUID customerId, String number, LocalDate due, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", customerId.toString());
        payload.put("invoiceNumber", number);
        payload.put("invoiceDate", due.minusDays(10).toString());
        payload.put("dueDate", due.toString());
        payload.put("amountExcludingTax", 100);
        payload.put("vat", 20);
        payload.put("amountIncludingTax", 120);
        payload.put("currency", "EUR");
        payload.put("status", status);
        return payload;
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
