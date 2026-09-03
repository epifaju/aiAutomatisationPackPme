package com.aipack;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class SettingsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void settingsAndAutomationsRequireAccessToken() {
        assertThat(restTemplate.getForEntity("/api/v1/settings", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity("/api/v1/automations", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getAndUpdateCompanySettings() throws Exception {
        String access = login();
        JsonNode data = getJson(access, "/api/v1/settings").path("data");
        assertThat(data.path("company").path("name").asText()).isEqualTo("Demo SAS");
        assertThat(data.path("ai").path("runtimeProvider").asText()).isNotBlank();
        assertThat(data.path("invoices").path("autoSendEnv").asBoolean()).isFalse();
        assertThat(data.path("security").path("dataRetentionDays").asInt()).isEqualTo(365);

        Map<String, Object> payload = Map.of(
                "company", Map.of("name", "Demo SAS", "timezone", "Europe/Paris", "country", "FR"),
                "reports", Map.of("dailyReportEmail", "direction@demo.aipack.example"));
        ResponseEntity<String> updated =
                restTemplate.exchange("/api/v1/settings", HttpMethod.PUT, json(access, payload), String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(updated.getBody()).path("data").path("reports").path("dailyReportEmail").asText())
                .isEqualTo("direction@demo.aipack.example");
    }

    @Test
    void automationsListAndRunReport() throws Exception {
        String access = login();
        JsonNode list = getJson(access, "/api/v1/automations").path("data");
        assertThat(list.isArray()).isTrue();
        assertThat(list.size()).isEqualTo(5);
        assertThat(list.get(0).path("id").asText()).isEqualTo("email-assistant");

        ResponseEntity<String> cannotRun = restTemplate.exchange(
                "/api/v1/automations/email-assistant/run", HttpMethod.POST, bearer(access), String.class);
        assertThat(cannotRun.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> ran = restTemplate.exchange(
                "/api/v1/automations/daily-report/run", HttpMethod.POST, bearer(access), String.class);
        assertThat(ran.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(ran.getBody()).path("data").path("message").asText()).contains("Rapport");
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
        return objectMapper.readTree(response.getBody());
    }

    private HttpEntity<Map<String, Object>> json(String accessToken, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        return new HttpEntity<>(Map.copyOf(body), headers);
    }

    private HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
