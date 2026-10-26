package pl.kdtraining.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    // Full list (e.g. "send to everyone" email) — not paginated.
    List<User> findAllByOrderByCreatedAtDesc();

    // Recipients of a marketing email — only people with active consent (opt-in). Not paginated.
    List<User> findAllByMarketingConsentAtIsNotNullOrderByCreatedAtDesc();

    // Unsubscribe from the link in the email — without logging in, by the user's stable token.
    Optional<User> findByMarketingUnsubscribeToken(UUID token);

    // Search by a fragment of first name, last name, or email (case-insensitive), paginated.
    @Query("""
        SELECT u FROM User u
        WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :q, '%'))
        """)
    Page<User> searchByPhrase(@Param("q") String q, Pageable pageable);

    // Variants excluding hidden accounts (technical/developer) — used when the list of excluded
    // emails is non-empty; they keep counters and pagination consistent (filter in SQL, not on the Java side).
    // :excluded must contain emails stored in lowercase.
    @Query("""
        SELECT u FROM User u
        WHERE LOWER(u.email) NOT IN :excluded
        """)
    Page<User> findAllExcludingEmails(@Param("excluded") Collection<String> excluded, Pageable pageable);

    @Query("""
        SELECT u FROM User u
        WHERE (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :q, '%')))
          AND LOWER(u.email) NOT IN :excluded
        """)
    Page<User> searchByPhraseExcludingEmails(@Param("q") String q,
                                             @Param("excluded") Collection<String> excluded,
                                             Pageable pageable);

    // Roster of 1-on-1 coaching clients. Backed by the partial index idx_users_athlete.
    List<User> findByAthleteTrueOrderByFirstNameAscLastNameAsc();

    // We treat emails case-insensitively (like all mail providers),
    // to avoid duplicate accounts and failed logins due to different letter casing.
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

    // Abandoned accounts after OAuth login: created (Google supplies name/email) but never completed
    // with a privacy-policy consent. We limit this to OAuth accounts (oauth_provider != NULL) — email/password
    // accounts have consent set at registration, and old accounts from before the privacy_accepted_at migration
    // also have NULL and must NOT be caught here. For automatic cleanup (GDPR — we don't keep data without consent).
    List<User> findByOauthProviderIsNotNullAndPrivacyAcceptedAtIsNullAndCreatedAtBefore(Instant cutoff);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);
}
