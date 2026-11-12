package com.ngolahealth.backend.modules.episodes.domain;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Episódio clínico. As regras do ciclo de vida estão aqui (e não no serviço), para nenhuma operação
 * as poder contornar. Fechado (concluído ou cancelado) é só de leitura.
 */
@Entity
@Table(name = "episodes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Episode extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "patient_id", nullable = false, updatable = false, length = 36)
  private UUID patientId;

  @Column(name = "doctor_id", length = 36)
  private UUID doctorId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 30)
  private EpisodeType type;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private EpisodeStatus status = EpisodeStatus.SCHEDULED;

  @Column(name = "scheduled_at", nullable = false)
  private Instant scheduledAt;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(columnDefinition = "text")
  private String symptoms;

  @Column(columnDefinition = "text")
  private String diagnosis;

  @Column(name = "clinical_notes", columnDefinition = "text")
  private String clinicalNotes;

  @Embedded private Vitals vitals;

  @Column(name = "vitals_recorded_at")
  private Instant vitalsRecordedAt;

  @Column(name = "vitals_recorded_by", length = 36)
  private UUID vitalsRecordedBy;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "started_by", length = 36)
  private UUID startedBy;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "completed_by", length = 36)
  private UUID completedBy;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancelled_by", length = 36)
  private UUID cancelledBy;

  @Column(name = "cancellation_reason", length = 500)
  private String cancellationReason;

  @Column(name = "created_by", length = 36, updatable = false)
  private UUID createdBy;

  @Version
  @Column(nullable = false)
  private long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Episode(
      UUID patientId,
      EpisodeType type,
      UUID doctorId,
      Instant scheduledAt,
      String reason,
      UUID createdBy) {
    this.patientId = patientId;
    this.type = type;
    this.doctorId = doctorId;
    this.scheduledAt = scheduledAt;
    this.reason = reason;
    this.createdBy = createdBy;
  }

  /** Dados de agendamento: só enquanto o episódio não começou. */
  public void reschedule(EpisodeType type, UUID doctorId, Instant scheduledAt, String reason) {
    requireStatus(EpisodeStatus.SCHEDULED, "Só um episódio agendado pode ser reagendado.");
    this.type = type;
    this.doctorId = doctorId;
    this.scheduledAt = scheduledAt;
    this.reason = reason;
  }

  /** Muda o médico responsável (permitido até o episódio fechar). */
  public void assignDoctor(UUID doctorId) {
    requireOpen();
    this.doctorId = doctorId;
  }

  public void start(UUID by, Instant at) {
    requireStatus(EpisodeStatus.SCHEDULED, "Só um episódio agendado pode ser iniciado.");
    this.status = EpisodeStatus.IN_PROGRESS;
    this.startedAt = at;
    this.startedBy = by;
  }

  public void recordVitals(Vitals vitals, UUID by, Instant at) {
    requireStatus(
        EpisodeStatus.IN_PROGRESS, "Os sinais vitais registam-se com o episódio em curso.");
    this.vitals = vitals == null || vitals.isEmpty() ? null : vitals;
    this.vitalsRecordedAt = this.vitals == null ? null : at;
    this.vitalsRecordedBy = this.vitals == null ? null : by;
  }

  public void recordClinicalNotes(String symptoms, String diagnosis, String clinicalNotes) {
    requireStatus(
        EpisodeStatus.IN_PROGRESS, "A informação clínica regista-se com o episódio em curso.");
    this.symptoms = symptoms;
    this.diagnosis = diagnosis;
    this.clinicalNotes = clinicalNotes;
  }

  /** Concluir exige diagnóstico, excepto em exames. */
  public void complete(UUID by, Instant at) {
    requireStatus(EpisodeStatus.IN_PROGRESS, "Só um episódio em curso pode ser concluído.");
    if (type != EpisodeType.EXAM && (diagnosis == null || diagnosis.isBlank())) {
      throw new BadRequestException(
          "DIAGNOSIS_REQUIRED", "Registe o diagnóstico antes de concluir o episódio.");
    }
    this.status = EpisodeStatus.COMPLETED;
    this.completedAt = at;
    this.completedBy = by;
  }

  public void cancel(String reason, UUID by, Instant at) {
    requireOpen();
    this.status = EpisodeStatus.CANCELLED;
    this.cancellationReason = reason;
    this.cancelledAt = at;
    this.cancelledBy = by;
  }

  private void requireOpen() {
    if (status.isClosed()) {
      throw new ConflictException(
          "EPISODE_CLOSED", "O episódio está fechado e já não pode ser alterado.");
    }
  }

  private void requireStatus(EpisodeStatus expected, String message) {
    requireOpen();
    if (status != expected) {
      throw new ConflictException("INVALID_EPISODE_STATUS", message);
    }
  }
}
