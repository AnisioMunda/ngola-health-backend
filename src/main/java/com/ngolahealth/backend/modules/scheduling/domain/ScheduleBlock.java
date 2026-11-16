package com.ngolahealth.backend.modules.scheduling.domain;

import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Ausência do médico num dia: inteiro (sem horas) ou num intervalo. */
@Entity
@Table(name = "schedule_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleBlock extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "doctor_id", nullable = false, updatable = false, length = 36)
  private UUID doctorId;

  @Column(name = "block_date", nullable = false, updatable = false)
  private LocalDate blockDate;

  @Column(name = "start_time", updatable = false)
  private LocalTime startTime;

  @Column(name = "end_time", updatable = false)
  private LocalTime endTime;

  @Column(nullable = false, updatable = false, length = 200)
  private String reason;

  @Column(name = "created_by", nullable = false, updatable = false, length = 36)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public ScheduleBlock(
      UUID doctorId,
      LocalDate date,
      LocalTime startTime,
      LocalTime endTime,
      String reason,
      UUID createdBy,
      Instant createdAt) {
    this.doctorId = doctorId;
    this.blockDate = date;
    this.startTime = startTime;
    this.endTime = endTime;
    this.reason = reason;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public boolean isAllDay() {
    return startTime == null;
  }

  /** Se o bloqueio apanha o intervalo [start, end) neste dia. */
  public boolean covers(LocalTime start, LocalTime end) {
    return isAllDay() || (startTime.isBefore(end) && start.isBefore(endTime));
  }
}
