package com.ngolahealth.backend.modules.prescriptions.domain;

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

/** Filtrado automaticamente pelo hospital ({@code @TenantId}). */
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

  @Query("SELECT p FROM Prescription p WHERE p.id = :id")
  Optional<Prescription> findScoped(@Param("id") UUID id);

  /** Bloqueia a prescrição durante a dispensa (evita ultrapassar a quantidade prescrita). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Prescription p WHERE p.id = :id")
  Optional<Prescription> findForUpdate(@Param("id") UUID id);

  @Query("SELECT p FROM Prescription p WHERE p.episodeId = :episodeId ORDER BY p.issuedAt DESC")
  List<Prescription> findByEpisode(@Param("episodeId") UUID episodeId);

  @Query(
      """
      SELECT p FROM Prescription p
      WHERE p.status IN :statuses
        AND (:patientId IS NULL OR p.patientId = :patientId)
        AND (:number IS NULL OR p.number = :number)
      """)
  Page<Prescription> search(
      @Param("statuses") Collection<PrescriptionStatus> statuses,
      @Param("patientId") UUID patientId,
      @Param("number") String number,
      Pageable pageable);
}
