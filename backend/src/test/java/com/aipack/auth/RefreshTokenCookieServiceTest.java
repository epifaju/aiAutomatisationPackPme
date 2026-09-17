package com.aipack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class RefreshTokenCookieServiceTest {

    @Test
    void productionUsesSecureHttpOnlyCookie() {
        RefreshTokenCookieService service = service("production", "auto");
        ResponseCookie cookie = service.issue("refresh-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(Duration.ofDays(7).getSeconds());
    }

    @Test
    void developmentKeepsCookieUsableOnHttp() {
        RefreshTokenCookieService service = service("development", "auto");
        ResponseCookie cookie = service.issue("refresh-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isFalse();
    }

    private static RefreshTokenCookieService service(String env, String secure) {
        return new RefreshTokenCookieService(
                new JwtProperties("test-jwt-secret-must-be-32-chars-min", Duration.ofMinutes(15), Duration.ofDays(7)),
                new JwtCookieProperties("aipack_refresh", secure, "Lax", "/api/v1/auth"),
                env);
    }
}
