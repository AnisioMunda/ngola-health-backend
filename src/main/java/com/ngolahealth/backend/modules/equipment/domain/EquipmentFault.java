package com.ngolahealth.backend.modules.equipment.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Avaria reportada. OPEN → RESOLVED (manutenção correctiva) ou DISMISSED (não era avaria). */
@Entity
@Table(name = "equipment_faults")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentFault extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "equipment_id", nullable = false, updatable = false, length = 36)
  private UUID equipmentId;

  @Column(nullable = false, updatable = false, length = 1000)
  private String description;

  @Column(name = "out_of_service", nullable = false, updatable = false)
  private boolean outOfService;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 15)
  private FaultStatus status = FaultStatus.OPEN;

  @Column(name = "reported_by", nullable = false, updatable = false, length = 36)
  private UUID reportedBy;

  @Column(name = "reported_at", nullable = false, updatable = false)
  private Instant reportedAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(length = 500)
  private String resolution;

  @Version
  @Column(nullable = false)
  private Long version;

  public EquipmentFault(
      UUID equipmentId,
      String description,
      boolean outOfService,
      UUID reportedBy,
      Instant reportedAt) {
    this.equipmentId = equipmentId;
    this.description = description;
    this.outOfService = outOfService;
    this.reportedBy = reportedBy;
    this.reportedAt = reportedAt;
  }

  public boolean isOpen() {
    return status == FaultStatus.OPEN;
  }

  public void resolve(String resolution, Instant at) {
    close(FaultStatus.RESOLVED, resolution, at);
  }

  public void dismiss(String reason, Instant at) {
    close(FaultStatus.DISMISSED, reason, at);
  }

  private void close(FaultStatus target, String text, Instant at) {
    if (!isOpen()) {
      throw new ConflictException("FAULT_CLOSED", "A avaria já foi fechada.");
    }
    this.status = target;
    this.resolution = text;
    this.resolvedAt = at;
  }
}
