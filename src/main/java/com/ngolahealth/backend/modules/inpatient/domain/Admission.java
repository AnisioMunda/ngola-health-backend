package com.ngolahealth.backend.modules.inpatient.domain;

import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
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

/** Internamento: paciente numa cama, com o seu episódio de internamento. */
@Entity
@Table(name = "admissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admission extends HospitalScopedEntity {

  /** Uma anulação corrige um registo errado; depois disto, o internamento termina com alta. */
  public static final Duration CANCEL_WINDOW = Duration.ofHours(24);

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(nullable = false, updatable = false, length = 20)
  private String number;

  @Column(name = "patient_id", nullable = false, updatable = false, length = 36)
  private UUID patientId;

  @Column(name = "episode_id", nullable = false, updatable = false, length = 36)
  private UUID episodeId;

  @Column(name = "origin_episode_id", updatable = false, length = 36)
  private UUID originEpisodeId;

  @Column(name = "ward_id", nullable = false, length = 36)
  private UUID wardId;

  @Column(name = "bed_id", nullable = false, length = 36)
  private UUID bedId;

  @Column(name = "responsible_doctor_id", nullable = false, length = 36)
  private UUID responsibleDoctorId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 15)
  private AdmissionStatus status = AdmissionStatus.ACTIVE;

  @Column(nullable = false, updatable = false, length = 500)
  private String reason;

  @Column(name = "admitted_by", nullable = false, updatable = false, length = 36)
  private UUID admittedBy;

  @Column(name = "admitted_at", nullable = false, updatable = false)
  private Instant admittedAt;

  @Column(name = "expected_discharge_date")
  private LocalDate expectedDischargeDate;

  @Column(name = "discharged_by", length = 36)
  private UUID dischargedBy;

  @Column(name = "discharged_at")
  private Instant dischargedAt;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "discharge_condition", length = 15)
  private DischargeCondition dischargeCondition;

  @Column(name = "discharge_diagnosis", length = 4000)
  private String dischargeDiagnosis;

  @Column(name = "discharge_summary", columnDefinition = "text")
  private String dischargeSummary;

  @Column(name = "cancelled_by", length = 36)
  private UUID cancelledBy;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancellation_reason", length = 500)
  private String cancellationReason;

  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Admission(
      String number,
      UUID patientId,
      UUID episodeId,
      UUID originEpisodeId,
      Bed bed,
      UUID responsibleDoctorId,
      String reason,
      LocalDate expectedDischargeDate,
      UUID admittedBy,
      Instant admittedAt) {
    this.number = number;
    this.patientId = patientId;
    this.episodeId = episodeId;
    this.originEpisodeId = originEpisodeId;
    this.wardId = bed.getWardId();
    this.bedId = bed.getId();
    this.responsibleDoctorId = responsibleDoctorId;
    this.reason = reason;
    this.expectedDischargeDate = expectedDischargeDate;
    this.admittedBy = admittedBy;
    this.admittedAt = admittedAt;
  }

  public boolean isActive() {
    return status == AdmissionStatus.ACTIVE;
  }

  public void moveTo(Bed bed) {
    requireActive();
    this.wardId = bed.getWardId();
    this.bedId = bed.getId();
  }

  public void assignDoctor(UUID doctorId) {
    requireActive();
    this.responsibleDoctorId = doctorId;
  }

  public void setExpectedDischargeDate(LocalDate date) {
    requireActive();
    this.expectedDischargeDate = date;
  }

  public void discharge(
      DischargeCondition condition, String diagnosis, String summary, UUID by, Instant at) {
    requireActive();
    this.status = AdmissionStatus.DISCHARGED;
    this.dischargeCondition = condition;
    this.dischargeDiagnosis = diagnosis;
    this.dischargeSummary = summary;
    this.dischargedBy = by;
    this.dischargedAt = at;
  }

  public void cancel(String reason, UUID by, Instant at) {
    requireActive();
    if (at.isAfter(admittedAt.plus(CANCEL_WINDOW))) {
      throw new ConflictException(
          "CANCEL_WINDOW_EXPIRED",
          "Um internamento só se anula nas primeiras 24 horas; depois, dá-se alta.");
    }
    this.status = AdmissionStatus.CANCELLED;
    this.cancellationReason = reason;
    this.cancelledBy = by;
    this.cancelledAt = at;
  }

  public void requireActive() {
    if (status != AdmissionStatus.ACTIVE) {
      throw new ConflictException(
          "ADMISSION_CLOSED",
          status == AdmissionStatus.DISCHARGED
              ? "O paciente já teve alta deste internamento."
              : "O internamento foi anulado.");
    }
  }
}
