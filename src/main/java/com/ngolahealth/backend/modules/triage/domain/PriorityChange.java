package com.ngolahealth.backend.modules.triage.domain;

import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Uma re-triagem. Imutável. */
@Entity
@Immutable
@Table(name = "triage_priority_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriorityChange extends HospitalScopedEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "triage_id", nullable = false, length = 36)
  private UUID triageId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "from_priority", nullable = false, length = 10)
  private TriagePriority fromPriority;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "to_priority", nullable = false, length = 10)
  private TriagePriority toPriority;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(name = "changed_by", nullable = false, length = 36)
  private UUID changedBy;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  public PriorityChange(
      UUID triageId,
      TriagePriority from,
      TriagePriority to,
      String reason,
      UUID changedBy,
      Instant changedAt) {
    this.id = UUID.randomUUID();
    this.triageId = triageId;
    this.fromPriority = from;
    this.toPriority = to;
    this.reason = reason;
    this.changedBy = changedBy;
    this.changedAt = changedAt;
  }
}
