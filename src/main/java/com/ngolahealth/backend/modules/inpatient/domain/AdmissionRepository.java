package com.ngolahealth.backend.modules.inpatient.domain;

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

public interface AdmissionRepository extends JpaRepository<Admission, UUID> {

  @Query("SELECT a FROM Admission a WHERE a.id = :id")
  Optional<Admission> findScoped(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM Admission a WHERE a.id = :id")
  Optional<Admission> findForUpdate(@Param("id") UUID id);

  @Query("SELECT a FROM Admission a WHERE a.episodeId = :episodeId")
  Optional<Admission> findByEpisode(@Param("episodeId") UUID episodeId);

  /**
   * Internamento activo do paciente, com leitura bloqueante (no MySQL uma leitura normal veria o
   * retrato do início da transacção e deixaria passar um internamento concorrente).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM Admission a WHERE a.patientId = :patientId AND a.status = 'ACTIVE'")
  List<Admission> lockActiveForPatient(@Param("patientId") UUID patientId);

  @Query(
      "SELECT a FROM Admission a WHERE a.status = 'ACTIVE' AND (:wardId IS NULL OR a.wardId = :wardId)")
  List<Admission> active(@Param("wardId") UUID wardId);

  @Query(
      """
      SELECT a FROM Admission a
      WHERE a.status IN :statuses
        AND (:wardId IS NULL OR a.wardId = :wardId)
        AND (:patientId IS NULL OR a.patientId = :patientId)
        AND (:doctorId IS NULL OR a.responsibleDoctorId = :doctorId)
      """)
  Page<Admission> search(
      @Param("statuses") Collection<AdmissionStatus> statuses,
      @Param("wardId") UUID wardId,
      @Param("patientId") UUID patientId,
      @Param("doctorId") UUID doctorId,
      Pageable pageable);
}
