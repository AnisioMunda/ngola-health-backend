package com.ngolahealth.backend.modules.triage.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtrado automaticamente pelo hospital do utilizador ({@code @TenantId}). */
public interface TriageRepository extends JpaRepository<TriageRecord, UUID> {

  @Query("SELECT t FROM TriageRecord t WHERE t.id = :id")
  Optional<TriageRecord> findScoped(@Param("id") UUID id);

  /** Fila activa (de qualquer dia: um doente pode atravessar a meia-noite em espera). */
  @Query("SELECT t FROM TriageRecord t WHERE t.status IN :statuses ORDER BY t.triagedAt")
  List<TriageRecord> findByStatusIn(@Param("statuses") Collection<TriageStatus> statuses);

  @Query("SELECT t FROM TriageRecord t WHERE t.queueDate = :day ORDER BY t.queueNumber DESC")
  List<TriageRecord> findByDay(@Param("day") LocalDate day);

  @Query("SELECT t FROM TriageRecord t WHERE t.patientId = :patientId ORDER BY t.triagedAt DESC")
  List<TriageRecord> findByPatient(@Param("patientId") UUID patientId);
}
