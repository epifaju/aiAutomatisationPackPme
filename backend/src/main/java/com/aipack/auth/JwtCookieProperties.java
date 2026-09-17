package com.aipack.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt.cookie")
public record JwtCookieProperties(String name, String secure, String sameSite, String path) {

    public String cookieName() {
        return name == null || name.isBlank() ? "aipack_refresh" : name;
    }

    public String sameSiteValue() {
        return sameSite == null || sameSite.isBlank() ? "Lax" : sameSite;
    }

    public String cookiePath() {
        return path == null || path.isBlank() ? "/api/v1/auth" : path;
    }
}
