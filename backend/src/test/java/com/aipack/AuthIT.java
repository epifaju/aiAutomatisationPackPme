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
class AuthIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginRefreshMeAndLogout() throws Exception {
        JsonNode login = postJson("/api/v1/auth/login", Map.of(
                "email", "demo.admin@aipack.example",
                "password", "DemoAdmin!2026"));
        assertThat(login.path("success").asBoolean()).isTrue();
        String access = login.path("data").path("accessToken").asText();
        String refresh = login.path("data").path("refreshToken").asText();
        assertThat(access).isNotBlank();
        assertThat(refresh).isNotBlank();
        assertThat(login.path("data").path("expiresIn").asLong()).isEqualTo(900);

        ResponseEntity<String> me = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, bearer(access), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode meBody = objectMapper.readTree(me.getBody());
        assertThat(meBody.path("data").path("email").asText()).isEqualTo("demo.admin@aipack.example");
        assertThat(meBody.path("data").path("role").asText()).isEqualTo("ADMIN");

        JsonNode rotated = postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh));
        String newRefresh = rotated.path("data").path("refreshToken").asText();
        assertThat(newRefresh).isNotBlank().isNotEqualTo(refresh);

        ResponseEntity<String> reuse = restTemplate.postForEntity(
                "/api/v1/auth/refresh",
                json(Map.of("refreshToken", refresh)),
                String.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Void> logout = restTemplate.exchange(
                "/api/v1/auth/logout",
                HttpMethod.POST,
                bearer(rotated.path("data").path("accessToken").asText()),
                Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> afterLogout = restTemplate.postForEntity(
                "/api/v1/auth/refresh",
                json(Map.of("refreshToken", newRefresh)),
                String.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginRejectsInvalidPassword() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                json(Map.of("email", "demo.admin@aipack.example", "password", "WrongPass1")),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void meRequiresAccessToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/auth/me", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private JsonNode postJson(String path, Map<String, String> body) throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(path, json(body), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    private HttpEntity<Map<String, String>> json(Map<String, String> body) {
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
