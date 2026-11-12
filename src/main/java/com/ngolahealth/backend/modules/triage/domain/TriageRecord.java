package com.ngolahealth.backend.modules.triage.domain;

import com.ngolahealth.backend.common.clinical.Vitals;
import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import com.ngolahealth.backend.modules.patients.domain.Gender;
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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Registo de triagem. As regras de estado estão aqui; fechado = só de leitura. */
@Entity
@Table(name = "triage_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TriageRecord extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "queue_date", nullable = false, updatable = false)
  private LocalDate queueDate;

  @Column(name = "queue_number", nullable = false, updatable = false)
  private int queueNumber;

  @Column(name = "patient_id", length = 36)
  private UUID patientId;

  @Column(name = "temp_name", length = 200)
  private String tempName;

  @Column(name = "temp_age")
  private Integer tempAge;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "temp_gender", length = 10)
  private Gender tempGender;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 10)
  private TriagePriority priority;

  @Column(name = "chief_complaint", nullable = false, length = 500)
  private String chiefComplaint;

  @Column(name = "pain_scale")
  private Integer painScale;

  @Column(columnDefinition = "text")
  private String notes;

  @Embedded private Vitals vitals;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 15)
  private TriageStatus status = TriageStatus.WAITING;

  @Column(name = "triaged_at", nullable = false, updatable = false)
  private Instant triagedAt;

  @Column(name = "triaged_by", nullable = false, updatable = false, length = 36)
  private UUID triagedBy;

  @Column(name = "called_at")
  private Instant calledAt;

  @Column(name = "called_by", length = 36)
  private UUID calledBy;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "closed_by", length = 36)
  private UUID closedBy;

  @Column(name = "episode_id", length = 36)
  private UUID episodeId;

  @Version
  @Column(nullable = false)
  private long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Doente registado ({@code patientId}) ou temporário ({@code tempName} obrigatório). */
  public TriageRecord(
      LocalDate queueDate,
      int queueNumber,
      UUID patientId,
      String tempName,
      Integer tempAge,
      Gender tempGender,
      TriagePriority priority,
      String chiefComplaint,
      Integer painScale,
      String notes,
      Vitals vitals,
      UUID triagedBy,
      Instant triagedAt) {
    if (patientId == null && (tempName == null || tempName.isBlank())) {
      throw new BadRequestException(
          "PATIENT_IDENTITY_REQUIRED",
          "Seleccione o paciente ou indique um nome provisório (ex.: \"Desconhecido\").");
    }
    this.queueDate = queueDate;
    this.queueNumber = queueNumber;
    this.patientId = patientId;
    if (patientId == null) {
      this.tempName = tempName;
      this.tempAge = tempAge;
      this.tempGender = tempGender;
    }
    this.priority = priority;
    this.chiefComplaint = chiefComplaint;
    this.painScale = painScale;
    this.notes = notes;
    this.vitals = vitals;
    this.triagedBy = triagedBy;
    this.triagedAt = triagedAt;
  }

  /** Re-triagem: só enquanto o doente espera. */
  public void changePriority(TriagePriority newPriority) {
    requireStatus(
        TriageStatus.WAITING, "Só é possível alterar a prioridade com o doente em espera.");
    if (newPriority == priority) {
      throw new BadRequestException("SAME_PRIORITY", "A prioridade indicada é a actual.");
    }
    this.priority = newPriority;
  }

  /** Associa o doente temporário ao paciente registado (permitido mesmo depois de fechado). */
  public void identify(UUID patientId) {
    if (this.patientId != null) {
      throw new ConflictException(
          "ALREADY_IDENTIFIED", "Esta triagem já está associada a um paciente.");
    }
    this.patientId = patientId;
  }

  public void call(UUID by, Instant at) {
    requireStatus(TriageStatus.WAITING, "Só um doente em espera pode ser chamado.");
    this.status = TriageStatus.IN_PROGRESS;
    this.calledAt = at;
    this.calledBy = by;
  }

  public void complete(UUID episodeId, UUID by, Instant at) {
    requireStatus(TriageStatus.IN_PROGRESS, "Só uma triagem em atendimento pode ser concluída.");
    this.status = TriageStatus.COMPLETED;
    this.episodeId = episodeId;
    this.closedAt = at;
    this.closedBy = by;
  }

  public void markLeft(UUID by, Instant at) {
    requireStatus(TriageStatus.WAITING, "Só um doente em espera pode ser dado como ausente.");
    this.status = TriageStatus.LEFT;
    this.closedAt = at;
    this.closedBy = by;
  }

  private void requireStatus(TriageStatus expected, String message) {
    if (status.isClosed()) {
      throw new ConflictException("TRIAGE_CLOSED", "A triagem está fechada.");
    }
    if (status != expected) {
      throw new ConflictException("INVALID_TRIAGE_STATUS", message);
    }
  }
}
