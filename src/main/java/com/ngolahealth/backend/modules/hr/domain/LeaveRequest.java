package com.ngolahealth.backend.modules.hr.domain;

import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Pedido de ausência: PENDING → APPROVED | REJECTED; PENDING ou APPROVED (por iniciar) → CANCELLED.
 */
@Entity
@Table(name = "leave_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false, length = 36)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, updatable = false, length = 15)
  private LeaveType type;

  @Column(name = "start_date", nullable = false, updatable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false, updatable = false)
  private LocalDate endDate;

  @Column(nullable = false, updatable = false)
  private int days;

  @Column(updatable = false, length = 500)
  private String reason;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 15)
  private LeaveStatus status = LeaveStatus.PENDING;

  @Column(name = "decided_by", length = 36)
  private UUID decidedBy;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decision_note", length = 300)
  private String decisionNote;

  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public LeaveRequest(UUID userId, LeaveType type, LocalDate start, LocalDate end, String reason) {
    this.userId = userId;
    this.type = type;
    this.startDate = start;
    this.endDate = end;
    this.days = (int) ChronoUnit.DAYS.between(start, end) + 1;
    this.reason = reason;
  }

  public boolean isActive() {
    return status == LeaveStatus.PENDING || status == LeaveStatus.APPROVED;
  }

  public void decide(boolean approve, String note, UUID by, Instant at) {
    if (status != LeaveStatus.PENDING) {
      throw new ConflictException(
          "LEAVE_ALREADY_DECIDED", "O pedido já foi decidido ou cancelado.");
    }
    if (userId.equals(by)) {
      throw new ForbiddenException("OWN_LEAVE", "Não pode decidir o seu próprio pedido.");
    }
    this.status = approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED;
    this.decisionNote = note;
    this.decidedBy = by;
    this.decidedAt = at;
  }

  public void cancel(LocalDate today) {
    if (status == LeaveStatus.APPROVED && !today.isBefore(startDate)) {
      throw new ConflictException(
          "LEAVE_STARTED", "Uma ausência aprovada só se cancela antes de começar.");
    }
    if (!isActive()) {
      throw new ConflictException("LEAVE_CLOSED", "O pedido já não pode ser cancelado.");
    }
    this.status = LeaveStatus.CANCELLED;
  }
}
