package com.ngolahealth.backend.modules.users.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

  /** Revoga todas as sessões activas do utilizador, excepto (opcionalmente) uma. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE UserSession s SET s.revokedAt = :now, s.revokedReason = :reason
      WHERE s.userId = :userId AND s.revokedAt IS NULL
        AND (:keep IS NULL OR s.id <> :keep)
      """)
  int revokeAll(
      @Param("userId") UUID userId,
      @Param("keep") UUID keep,
      @Param("now") Instant now,
      @Param("reason") String reason);

  /** Apaga sessões expiradas ou revogadas antes de {@code cutoff}. */
  @Modifying
  @Query(
      "DELETE FROM UserSession s WHERE s.expiresAt < :cutoff"
          + " OR (s.revokedAt IS NOT NULL AND s.revokedAt < :cutoff)")
  int deleteStale(@Param("cutoff") Instant cutoff);
}
