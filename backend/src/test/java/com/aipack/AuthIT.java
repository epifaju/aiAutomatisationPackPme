package com.aipack;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
    void loginRefreshMeAndLogoutUseHttpOnlyRefreshCookie() throws Exception {
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login",
                json(Map.of("email", "demo.admin@aipack.example", "password", "DemoAdmin!2026")),
                String.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode login = objectMapper.readTree(loginResponse.getBody());
        assertThat(login.path("success").asBoolean()).isTrue();
        String access = login.path("data").path("accessToken").asText();
        assertThat(access).isNotBlank();
        assertThat(login.path("data").path("refreshToken").isMissingNode()
                        || login.path("data").path("refreshToken").isNull())
                .isTrue();
        assertThat(login.path("data").path("expiresIn").asLong()).isEqualTo(900);

        String setCookie = firstSetCookie(loginResponse);
        assertThat(setCookie).startsWith("aipack_refresh=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).containsIgnoringCase("Path=/api/v1/auth");
        assertThat(setCookie).doesNotContain("Secure");
        String refreshCookie = cookieHeader(setCookie);

        ResponseEntity<String> me = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, bearer(access), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode meBody = objectMapper.readTree(me.getBody());
        assertThat(meBody.path("data").path("email").asText()).isEqualTo("demo.admin@aipack.example");
        assertThat(meBody.path("data").path("role").asText()).isEqualTo("ADMIN");

        ResponseEntity<String> rotated = restTemplate.postForEntity(
                "/api/v1/auth/refresh", cookieOnly(refreshCookie), String.class);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode rotatedBody = objectMapper.readTree(rotated.getBody());
        String newAccess = rotatedBody.path("data").path("accessToken").asText();
        assertThat(newAccess).isNotBlank();
        assertThat(rotatedBody.path("data").path("refreshToken").isMissingNode()
                        || rotatedBody.path("data").path("refreshToken").isNull())
                .isTrue();
        String newRefreshCookie = cookieHeader(firstSetCookie(rotated));
        assertThat(newRefreshCookie).isNotEqualTo(refreshCookie);

        ResponseEntity<String> reuse = restTemplate.postForEntity(
                "/api/v1/auth/refresh", cookieOnly(refreshCookie), String.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Void> logout = restTemplate.exchange(
                "/api/v1/auth/logout",
                HttpMethod.POST,
                bearer(newAccess, newRefreshCookie),
                Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(firstSetCookie(logout)).contains("Max-Age=0");

        ResponseEntity<String> afterLogout = restTemplate.postForEntity(
                "/api/v1/auth/refresh", cookieOnly(newRefreshCookie), String.class);
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

    @Test
    void refreshWithoutCookieIsUnauthorized() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/refresh", json(Map.of()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpEntity<Map<String, String>> json(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> cookieOnly(String cookieHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> bearer(String accessToken) {
        return bearer(accessToken, null);
    }

    private HttpEntity<Void> bearer(String accessToken, String cookieHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        if (cookieHeader != null) {
            headers.add(HttpHeaders.COOKIE, cookieHeader);
        }
        return new HttpEntity<>(headers);
    }

    private static String firstSetCookie(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotEmpty();
        return cookies.getFirst();
    }

    private static String cookieHeader(String setCookie) {
        String pair = setCookie.split(";", 2)[0];
        assertThat(pair).contains("=");
        return pair;
    }
}
