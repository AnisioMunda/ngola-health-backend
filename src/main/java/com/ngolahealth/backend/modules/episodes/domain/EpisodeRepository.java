package com.ngolahealth.backend.modules.episodes.domain;

import com.ngolahealth.backend.common.web.CappedCount;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtrado automaticamente pelo hospital do utilizador ({@code @TenantId}). */
public interface EpisodeRepository extends JpaRepository<Episode, UUID> {

  @Query("SELECT e FROM Episode e WHERE e.id = :id")
  Optional<Episode> findScoped(@Param("id") UUID id);

  /**
   * {@code anyStatus} ignora {@code statuses} (nunca vazio); {@code to} exclusivo; ordenação pelo
   * {@code Pageable}.
   */
  @Query(
      value =
          """
          SELECT e FROM Episode e
          WHERE (:anyStatus = TRUE OR e.status IN :statuses)
            AND (:patientId IS NULL OR e.patientId = :patientId)
            AND (:doctorId IS NULL OR e.doctorId = :doctorId)
            AND (:type IS NULL OR e.type = :type)
            AND (:from IS NULL OR e.scheduledAt >= :from)
            AND (:to IS NULL OR e.scheduledAt < :to)
          """,
      countQuery =
          """
          SELECT COUNT(*) FROM (SELECT e.id AS id FROM Episode e
          WHERE (:anyStatus = TRUE OR e.status IN :statuses)
            AND (:patientId IS NULL OR e.patientId = :patientId)
            AND (:doctorId IS NULL OR e.doctorId = :doctorId)
            AND (:type IS NULL OR e.type = :type)
            AND (:from IS NULL OR e.scheduledAt >= :from)
            AND (:to IS NULL OR e.scheduledAt < :to)
          ORDER BY e.scheduledAt DESC, e.id DESC
          """
              + CappedCount.SUBQUERY_LIMIT
              + ") c")
  Page<Episode> search(
      @Param("anyStatus") boolean anyStatus,
      @Param("statuses") Collection<EpisodeStatus> statuses,
      @Param("patientId") UUID patientId,
      @Param("doctorId") UUID doctorId,
      @Param("type") EpisodeType type,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}
