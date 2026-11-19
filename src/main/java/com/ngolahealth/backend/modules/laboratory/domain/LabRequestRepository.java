package com.ngolahealth.backend.modules.laboratory.domain;

import com.ngolahealth.backend.common.web.CappedCount;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabRequestRepository extends JpaRepository<LabRequest, UUID> {

  @Query("SELECT r FROM LabRequest r WHERE r.id = :id")
  Optional<LabRequest> findScoped(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM LabRequest r WHERE r.id = :id")
  Optional<LabRequest> findForUpdate(@Param("id") UUID id);

  @Query("SELECT r FROM LabRequest r WHERE r.episodeId = :episodeId ORDER BY r.requestedAt DESC")
  List<LabRequest> findByEpisode(@Param("episodeId") UUID episodeId);

  @Query(
      value =
          """
          SELECT r FROM LabRequest r
          WHERE (:anyStatus = TRUE OR r.status IN :statuses)
            AND (:patientId IS NULL OR r.patientId = :patientId)
            AND (:number IS NULL OR r.number = :number)
          """,
      countQuery =
          """
          SELECT COUNT(*) FROM (SELECT r.id AS id FROM LabRequest r
          WHERE (:anyStatus = TRUE OR r.status IN :statuses)
            AND (:patientId IS NULL OR r.patientId = :patientId)
            AND (:number IS NULL OR r.number = :number)
          ORDER BY r.requestedAt DESC, r.id DESC
          """
              + CappedCount.SUBQUERY_LIMIT
              + ") c")
  Page<LabRequest> search(
      @Param("anyStatus") boolean anyStatus,
      @Param("statuses") Collection<LabRequestStatus> statuses,
      @Param("patientId") UUID patientId,
      @Param("number") String number,
      Pageable pageable);
}
