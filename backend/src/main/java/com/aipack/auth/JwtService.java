package com.aipack.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String createAccessToken(AccessTokenSubject subject) {
        Instant now = Instant.now();
        Instant expires = now.plus(properties.accessTokenTtl());
        return Jwts.builder()
                .subject(subject.id().toString())
                .claim("email", subject.email())
                .claim("role", subject.role())
                .claim("companyId", subject.companyId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
    }

    public AuthUser parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return new AuthUser(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get("companyId", String.class)),
                    claims.get("email", String.class),
                    claims.get("role", String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            throw AuthException.invalidToken();
        }
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    public record AccessTokenSubject(UUID id, UUID companyId, String email, String role) {}
}
