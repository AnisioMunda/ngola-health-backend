package com.ngolahealth.backend.modules.scheduling.domain;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Marcação de um paciente numa vaga de um médico. As transições de estado estão aqui. */
@Entity
@Table(name = "appointments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "patient_id", nullable = false, updatable = false, length = 36)
  private UUID patientId;

  @Column(name = "doctor_id", nullable = false, updatable = false, length = 36)
  private UUID doctorId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, updatable = false, length = 20)
  private AppointmentType type;

  @Column(name = "appointment_date", nullable = false, updatable = false)
  private LocalDate date;

  @Column(name = "start_time", nullable = false, updatable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false, updatable = false)
  private LocalTime endTime;

  @Column(name = "slot_position", nullable = false, updatable = false)
  private int slotPosition;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 15)
  private AppointmentStatus status = AppointmentStatus.SCHEDULED;

  @Column(nullable = false, updatable = false, length = 300)
  private String reason;

  @Column(length = 1000, updatable = false)
  private String notes;

  @Column(name = "booked_by", nullable = false, updatable = false, length = 36)
  private UUID bookedBy;

  @Column(name = "booked_at", nullable = false, updatable = false)
  private Instant bookedAt;

  @Column(name = "confirmed_by", length = 36)
  private UUID confirmedBy;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "checked_in_by", length = 36)
  private UUID checkedInBy;

  @Column(name = "checked_in_at")
  private Instant checkedInAt;

  @Column(name = "episode_id", length = 36)
  private UUID episodeId;

  @Column(name = "no_show_by", length = 36)
  private UUID noShowBy;

  @Column(name = "no_show_at")
  private Instant noShowAt;

  @Column(name = "cancelled_by", length = 36)
  private UUID cancelledBy;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancellation_reason", length = 300)
  private String cancellationReason;

  @Column(name = "rescheduled_from_id", updatable = false, length = 36)
  private UUID rescheduledFromId;

  @Column(name = "rescheduled_to_id", length = 36)
  private UUID rescheduledToId;

  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Appointment(
      UUID patientId,
      UUID doctorId,
      AppointmentType type,
      LocalDate date,
      LocalTime startTime,
      LocalTime endTime,
      int slotPosition,
      String reason,
      String notes,
      UUID rescheduledFromId,
      UUID bookedBy,
      Instant bookedAt) {
    this.patientId = patientId;
    this.doctorId = doctorId;
    this.type = type;
    this.date = date;
    this.startTime = startTime;
    this.endTime = endTime;
    this.slotPosition = slotPosition;
    this.reason = reason;
    this.notes = notes;
    this.rescheduledFromId = rescheduledFromId;
    this.bookedBy = bookedBy;
    this.bookedAt = bookedAt;
  }

  public LocalDateTime startsAt() {
    return LocalDateTime.of(date, startTime);
  }

  public boolean overlaps(LocalTime start, LocalTime end) {
    return startTime.isBefore(end) && start.isBefore(endTime);
  }

  public void confirm(UUID by, Instant at) {
    if (status != AppointmentStatus.SCHEDULED) {
      throw invalid("Só se confirma uma marcação agendada.");
    }
    this.status = AppointmentStatus.CONFIRMED;
    this.confirmedBy = by;
    this.confirmedAt = at;
  }

  /** O paciente chegou: fica ligado ao episódio aberto para ele. */
  public void checkIn(LocalDate today, UUID episodeId, UUID by, Instant at) {
    requirePending();
    if (!date.equals(today)) {
      throw new ConflictException(
          "NOT_APPOINTMENT_DAY", "A chegada só se regista no dia da marcação.");
    }
    this.status = AppointmentStatus.CHECKED_IN;
    this.episodeId = episodeId;
    this.checkedInBy = by;
    this.checkedInAt = at;
  }

  public void markNoShow(LocalDateTime now, UUID by, Instant at) {
    requirePending();
    if (now.isBefore(startsAt())) {
      throw new ConflictException(
          "TOO_EARLY_FOR_NO_SHOW", "A falta só se regista depois da hora marcada.");
    }
    this.status = AppointmentStatus.NO_SHOW;
    this.noShowBy = by;
    this.noShowAt = at;
  }

  public void cancel(String reason, UUID by, Instant at) {
    requirePending();
    this.status = AppointmentStatus.CANCELLED;
    this.cancellationReason = reason;
    this.cancelledBy = by;
    this.cancelledAt = at;
  }

  /** Liga esta marcação (já cancelada pela remarcação) à que a substitui. */
  public void rescheduledTo(UUID newId) {
    if (status != AppointmentStatus.CANCELLED) {
      throw new IllegalStateException("Só uma marcação cancelada é substituída.");
    }
    this.rescheduledToId = newId;
  }

  public void requirePending() {
    if (!status.isPending()) {
      throw invalid(
          switch (status) {
            case CHECKED_IN -> "O paciente já chegou; a marcação está fechada.";
            case NO_SHOW -> "A marcação está registada como falta.";
            case CANCELLED -> "A marcação foi cancelada.";
            default -> "Estado inválido.";
          });
    }
  }

  private static ConflictException invalid(String message) {
    return new ConflictException("INVALID_APPOINTMENT_STATUS", message);
  }
}
