package com.aipack;

import static org.assertj.core.api.Assertions.assertThat;

import com.aipack.auth.DemoAdminPasswordReconciler;
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
class RbacIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void userCanReadSettingsButCannotMutateSensitiveAdminEndpoints() throws Exception {
        String userAccess = login(DemoAdminPasswordReconciler.DEMO_USER_EMAIL, DemoAdminPasswordReconciler.DEMO_USER_PASSWORD);

        ResponseEntity<String> me = restTemplate.exchange("/api/v1/auth/me", HttpMethod.GET, bearer(userAccess), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(me.getBody()).path("data").path("role").asText()).isEqualTo("USER");

        ResponseEntity<String> getSettings =
                restTemplate.exchange("/api/v1/settings", HttpMethod.GET, bearer(userAccess), String.class);
        assertThat(getSettings.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> getAutomations =
                restTemplate.exchange("/api/v1/automations", HttpMethod.GET, bearer(userAccess), String.class);
        assertThat(getAutomations.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> putSettings = restTemplate.exchange(
                "/api/v1/settings",
                HttpMethod.PUT,
                json(userAccess, Map.of("email", Map.of("autoSendCompany", true))),
                String.class);
        assertForbidden(putSettings);

        ResponseEntity<String> rotate = restTemplate.exchange(
                "/api/v1/settings/webhook-secret/rotate",
                HttpMethod.POST,
                bearer(userAccess),
                String.class);
        assertForbidden(rotate);

        ResponseEntity<String> autoSend = restTemplate.exchange(
                "/api/v1/automations/email-assistant/auto-send",
                HttpMethod.POST,
                json(userAccess, Map.of("enabled", true)),
                String.class);
        assertForbidden(autoSend);
    }

    @Test
    void adminCanUpdateSettings() throws Exception {
        String adminAccess = login(DemoAdminPasswordReconciler.DEMO_EMAIL, DemoAdminPasswordReconciler.DEMO_PASSWORD);
        ResponseEntity<String> putSettings = restTemplate.exchange(
                "/api/v1/settings",
                HttpMethod.PUT,
                json(adminAccess, Map.of("company", Map.of("timezone", "Europe/Paris"))),
                String.class);
        assertThat(putSettings.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void assertForbidden(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("FORBIDDEN");
    }

    private String login(String email, String password) throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login", json(null, Map.of("email", email, "password", password)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).path("data").path("accessToken").asText();
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
