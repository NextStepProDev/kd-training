package pl.kdtraining.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHashAndTokenType(String tokenHash, TokenType tokenType);

    @Query("SELECT t FROM AuthToken t WHERE t.tokenHash = :tokenHash AND t.tokenType = :tokenType " +
           "AND t.expiresAt > :now AND t.usedAt IS NULL")
    Optional<AuthToken> findValidToken(String tokenHash, TokenType tokenType, Instant now);

    // Like findValidToken, but tolerates a recently rotated token (usedAt within the grace
    // window). Used only for refresh-token rotation: concurrent browser tabs share one refresh
    // token and race to rotate it — without the grace window the losing tabs get logged out.
    @Query("SELECT t FROM AuthToken t WHERE t.tokenHash = :tokenHash AND t.tokenType = :tokenType " +
           "AND t.expiresAt > :now AND (t.usedAt IS NULL OR t.usedAt > :graceThreshold)")
    Optional<AuthToken> findRefreshableToken(String tokenHash, TokenType tokenType, Instant now, Instant graceThreshold);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredTokens(Instant cutoff);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.user.id = :userId AND t.tokenType = :tokenType")
    void deleteByUserIdAndTokenType(UUID userId, TokenType tokenType);

    @Query("SELECT COUNT(t) > 0 FROM AuthToken t WHERE t.user.id = :userId AND t.tokenType = :tokenType " +
           "AND t.createdAt > :since AND t.usedAt IS NULL")
    boolean hasRecentUnusedToken(UUID userId, TokenType tokenType, Instant since);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.user.id = :userId")
    void deleteAllByUserId(UUID userId);
}
