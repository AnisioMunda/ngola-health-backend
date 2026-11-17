package com.ngolahealth.backend.modules.hr.domain;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Registo de ponto: entrada e saída reais, com o turno a que correspondem (se houver). Atraso e
 * horas extra calculam-se contra o turno.
 */
@Entity
@Table(name = "attendance_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceRecord extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false, length = 36)
  private UUID userId;

  @Column(name = "shift_id", length = 36)
  private UUID shiftId;

  @Column(name = "clock_in", nullable = false)
  private Instant clockIn;

  @Column(name = "clock_out")
  private Instant clockOut;

  @Column(name = "minutes_worked")
  private Integer minutesWorked;

  @Column(name = "late_minutes", nullable = false)
  private int lateMinutes;

  @Column(name = "overtime_minutes", nullable = false)
  private int overtimeMinutes;

  @Column(name = "corrected_by", length = 36)
  private UUID correctedBy;

  @Column(name = "corrected_at")
  private Instant correctedAt;

  @Column(name = "correction_reason", length = 300)
  private String correctionReason;

  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public AttendanceRecord(UUID userId, Shift shift, Instant clockIn) {
    this.userId = userId;
    this.clockIn = clockIn;
    attach(shift);
  }

  public boolean isOpen() {
    return clockOut == null;
  }

  public void clockOut(Shift shift, Instant at) {
    this.clockOut = at;
    attach(shift);
  }

  /** Correcção por um gestor (esquecimento, erro): fica registado quem, quando e porquê. */
  public void correct(Instant in, Instant out, Shift shift, String reason, UUID by, Instant at) {
    if (out != null && !in.isBefore(out)) {
      throw new BadRequestException("INVALID_PERIOD", "A entrada tem de ser anterior à saída.");
    }
    this.clockIn = in;
    this.clockOut = out;
    this.correctionReason = reason;
    this.correctedBy = by;
    this.correctedAt = at;
    attach(shift);
  }

  private void attach(Shift shift) {
    this.shiftId = shift == null ? null : shift.getId();
    this.lateMinutes =
        shift == null
            ? 0
            : (int) Math.max(0, Duration.between(shift.getStartsAt(), clockIn).toMinutes());
    if (clockOut == null) {
      this.minutesWorked = null;
      this.overtimeMinutes = 0;
    } else {
      this.minutesWorked = (int) Duration.between(clockIn, clockOut).toMinutes();
      this.overtimeMinutes =
          shift == null
              ? 0
              : (int) Math.max(0, Duration.between(shift.getEndsAt(), clockOut).toMinutes());
    }
  }
}
