package com.aipack.auth;

import com.aipack.audit.AuditRecord;
import com.aipack.audit.AuditService;
import com.aipack.audit.AuditStatus;
import com.aipack.auth.dto.LoginRequest;
import com.aipack.auth.dto.TokenResponse;
import com.aipack.auth.dto.UserMeResponse;
import com.aipack.identity.RefreshToken;
import com.aipack.identity.RefreshTokenRepository;
import com.aipack.identity.User;
import com.aipack.identity.UserMapper;
import com.aipack.identity.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            UserMapper userMapper,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        List<User> matches = userRepository.findAllByEmailIgnoreCase(request.email().trim());
        User user = matches.size() == 1 ? matches.getFirst() : null;
        if (user == null || !user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw AuthException.unauthorized();
        }
        user.setLastLoginAt(Instant.now());
        TokenResponse tokens = issueTokens(user);
        auditUser(user, "LOGIN");
        return tokens;
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        RefreshToken stored = refreshTokenRepository
                .findByTokenHashWithUser(sha256(rawRefreshToken))
                .orElseThrow(AuthException::invalidToken);
        if (!stored.isActive(now)) {
            if (stored.getRevokedAt() != null) {
                refreshTokenRepository.revokeAllActiveForUser(stored.getUser().getId(), now);
            }
            throw AuthException.invalidToken();
        }
        User user = stored.getUser();
        if (!user.isEnabled()) {
            throw AuthException.unauthorized();
        }
        TokenResponse tokens = issueTokens(user);
        stored.setRevokedAt(now);
        stored.setReplacedBy(refreshTokenRepository
                .findByTokenHash(sha256(tokens.refreshToken()))
                .map(RefreshToken::getId)
                .orElse(null));
        return tokens;
    }

    @Transactional
    public void logout(AuthUser principal) {
        refreshTokenRepository.revokeAllActiveForUser(principal.id(), Instant.now());
        auditService.record(new AuditRecord(
                principal.companyId(),
                null,
                "LOGOUT",
                "USER",
                principal.id().toString(),
                AuditStatus.SUCCESS,
                Map.of("email", principal.email())));
    }

    @Transactional(readOnly = true)
    public UserMeResponse me(AuthUser principal) {
        User loaded = userRepository
                .findByIdWithCompany(principal.id())
                .orElseThrow(AuthException::invalidToken);
        return userMapper.toMeResponse(loaded);
    }

    private void auditUser(User user, String action) {
        auditService.record(new AuditRecord(
                user.getCompany().getId(),
                null,
                action,
                "USER",
                user.getId().toString(),
                AuditStatus.SUCCESS,
                Map.of("email", user.getEmail())));
    }

    private TokenResponse issueTokens(User user) {
        String access = jwtService.createAccessToken(new JwtService.AccessTokenSubject(
                user.getId(), user.getCompany().getId(), user.getEmail(), user.getRole()));
        String rawRefresh = newRefreshToken();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(sha256(rawRefresh));
        entity.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()));
        refreshTokenRepository.save(entity);
        return new TokenResponse(access, rawRefresh, "Bearer", jwtService.accessTokenTtlSeconds());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
