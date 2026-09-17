package com.aipack.auth;

import com.aipack.config.ProductionSecretsValidator;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(JwtCookieProperties.class)
public class RefreshTokenCookieService {

    private final JwtProperties jwtProperties;
    private final JwtCookieProperties cookieProperties;
    private final String appEnv;

    public RefreshTokenCookieService(
            JwtProperties jwtProperties,
            JwtCookieProperties cookieProperties,
            @Value("${app.env:development}") String appEnv) {
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
        this.appEnv = appEnv;
    }

    public ResponseCookie issue(String refreshToken) {
        return base(refreshToken)
                .maxAge(jwtProperties.refreshTokenTtl())
                .build();
    }

    public ResponseCookie clear() {
        return base("")
                .maxAge(0)
                .build();
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String expected = cookieProperties.cookieName();
        for (Cookie cookie : cookies) {
            if (expected.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    boolean secureCookies() {
        String mode = cookieProperties.secure();
        if (mode != null && "true".equalsIgnoreCase(mode.trim())) {
            return true;
        }
        if (mode != null && "false".equalsIgnoreCase(mode.trim())) {
            return false;
        }
        return ProductionSecretsValidator.isProduction(appEnv);
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(cookieProperties.cookieName(), value)
                .httpOnly(true)
                .secure(secureCookies())
                .sameSite(cookieProperties.sameSiteValue())
                .path(cookieProperties.cookiePath());
    }
}
