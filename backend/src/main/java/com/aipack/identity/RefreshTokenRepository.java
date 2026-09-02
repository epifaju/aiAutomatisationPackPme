package com.aipack.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query(
            """
            SELECT t FROM RefreshToken t
            JOIN FETCH t.user u
            JOIN FETCH u.company
            WHERE t.tokenHash = :hash
            """)
    Optional<RefreshToken> findByTokenHashWithUser(@Param("hash") String hash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE RefreshToken t
            SET t.revokedAt = :now, t.updatedAt = :now
            WHERE t.user.id = :userId AND t.revokedAt IS NULL
            """)
    int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
