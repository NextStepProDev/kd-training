package pl.mocnygrzbiet.domain.invitation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    @Query("SELECT i FROM Invitation i WHERE i.tokenHash = :tokenHash AND i.expiresAt > :now")
    Optional<Invitation> findValid(String tokenHash, Instant now);

    @Query("SELECT i FROM Invitation i WHERE i.expiresAt > :now ORDER BY i.createdAt DESC")
    List<Invitation> findAllValid(Instant now);

    @Query("SELECT i FROM Invitation i WHERE LOWER(i.email) = LOWER(:email) AND i.expiresAt > :now")
    Optional<Invitation> findValidForEmail(String email, Instant now);

    /** Clears the way for a new invitation: the unique index allows one row per address, expired or not. */
    @Modifying
    @Query("DELETE FROM Invitation i WHERE LOWER(i.email) = LOWER(:email)")
    int deleteByEmail(String email);

    /** The count lets cancelling tell "deleted" from "nothing to delete" (a 404) without a read first. */
    @Modifying
    @Query("DELETE FROM Invitation i WHERE i.id = :id")
    int deleteByIdReturningCount(UUID id);

    @Modifying
    @Query("DELETE FROM Invitation i WHERE i.expiresAt < :cutoff")
    int deleteExpired(Instant cutoff);
}
