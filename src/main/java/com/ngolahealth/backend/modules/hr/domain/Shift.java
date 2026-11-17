package com.ngolahealth.backend.modules.hr.domain;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Turno de um profissional. Não se edita: cancela-se e cria-se outro. */
@Entity
@Table(name = "shifts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shift extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false, length = 36)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, updatable = false, length = 15)
  private ShiftType type;

  @Column(name = "starts_at", nullable = false, updatable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false, updatable = false)
  private Instant endsAt;

  @Column(name = "ward_id", updatable = false, length = 36)
  private UUID wardId;

  @Column(updatable = false, length = 100)
  private String department;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 15)
  private ShiftStatus status = ShiftStatus.SCHEDULED;

  @Column(updatable = false, length = 300)
  private String notes;

  @Column(name = "created_by", nullable = false, updatable = false, length = 36)
  private UUID createdBy;

  @Column(name = "cancelled_by", length = 36)
  private UUID cancelledBy;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancellation_reason", length = 300)
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

  public Shift(
      UUID userId,
      ShiftType type,
      Instant startsAt,
      Instant endsAt,
      UUID wardId,
      String department,
      String notes,
      UUID createdBy) {
    this.userId = userId;
    this.type = type;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.wardId = wardId;
    this.department = department;
    this.notes = notes;
    this.createdBy = createdBy;
  }

  public boolean isActive() {
    return status == ShiftStatus.SCHEDULED;
  }

  public boolean overlaps(Instant from, Instant to) {
    return startsAt.isBefore(to) && from.isBefore(endsAt);
  }

  public void cancel(String reason, UUID by, Instant at) {
    if (!isActive()) {
      throw new ConflictException("SHIFT_CANCELLED", "O turno já está cancelado.");
    }
    this.status = ShiftStatus.CANCELLED;
    this.cancellationReason = reason;
    this.cancelledBy = by;
    this.cancelledAt = at;
  }
}
