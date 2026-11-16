package com.ngolahealth.backend.modules.scheduling.domain;

import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Bloco semanal de consultas: num dia da semana, das {@code startTime} às {@code endTime}, vagas de
 * {@code slotMinutes} com {@code capacity} pacientes cada. Não se edita: desactiva-se e cria-se
 * outro, para as marcações feitas continuarem a corresponder ao horário com que foram feitas.
 */
@Entity
@Table(name = "doctor_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DoctorSchedule extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "doctor_id", nullable = false, updatable = false, length = 36)
  private UUID doctorId;

  /** ISO: 1 = segunda … 7 = domingo. */
  @Column(name = "day_of_week", nullable = false, updatable = false)
  private int dayOfWeek;

  @Column(name = "start_time", nullable = false, updatable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false, updatable = false)
  private LocalTime endTime;

  @Column(name = "slot_minutes", nullable = false, updatable = false)
  private int slotMinutes;

  @Column(nullable = false, updatable = false)
  private int capacity;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_by", nullable = false, updatable = false, length = 36)
  private UUID createdBy;

  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public DoctorSchedule(
      UUID doctorId,
      DayOfWeek day,
      LocalTime startTime,
      LocalTime endTime,
      int slotMinutes,
      int capacity,
      UUID createdBy) {
    this.doctorId = doctorId;
    this.dayOfWeek = day.getValue();
    this.startTime = startTime;
    this.endTime = endTime;
    this.slotMinutes = slotMinutes;
    this.capacity = capacity;
    this.createdBy = createdBy;
  }

  public DayOfWeek day() {
    return DayOfWeek.of(dayOfWeek);
  }

  public void deactivate() {
    this.active = false;
  }

  public boolean overlaps(LocalTime start, LocalTime end) {
    return startTime.isBefore(end) && start.isBefore(endTime);
  }

  /** Inícios das vagas; um resto no fim do bloco, mais curto do que uma vaga, não conta. */
  public List<LocalTime> slotStarts() {
    var starts = new ArrayList<LocalTime>();
    int end = endTime.toSecondOfDay() / 60;
    for (int m = startTime.toSecondOfDay() / 60; m + slotMinutes <= end; m += slotMinutes) {
      starts.add(LocalTime.ofSecondOfDay(m * 60L));
    }
    return starts;
  }

  public boolean isSlotStart(LocalTime time) {
    return slotStarts().contains(time);
  }
}
