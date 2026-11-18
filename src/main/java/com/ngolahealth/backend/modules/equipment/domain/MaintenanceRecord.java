package com.ngolahealth.backend.modules.equipment.domain;

import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Manutenção registada (imutável). */
@Entity
@Table(name = "maintenance_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaintenanceRecord extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "equipment_id", nullable = false, updatable = false, length = 36)
  private UUID equipmentId;

  @Column(name = "fault_id", updatable = false, length = 36)
  private UUID faultId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, updatable = false, length = 15)
  private MaintenanceType type;

  @Column(name = "performed_on", nullable = false, updatable = false)
  private LocalDate performedOn;

  @Column(name = "performed_by", nullable = false, updatable = false, length = 200)
  private String performedBy;

  @Column(nullable = false, updatable = false, length = 2000)
  private String description;

  @Column(name = "parts_replaced", updatable = false, length = 500)
  private String partsReplaced;

  @Column(updatable = false, precision = 14, scale = 2)
  private BigDecimal cost;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, updatable = false, length = 10)
  private MaintenanceResult result;

  @Column(name = "recorded_by", nullable = false, updatable = false, length = 36)
  private UUID recordedBy;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  public MaintenanceRecord(
      UUID equipmentId,
      UUID faultId,
      MaintenanceType type,
      LocalDate performedOn,
      String performedBy,
      String description,
      String partsReplaced,
      BigDecimal cost,
      MaintenanceResult result,
      UUID recordedBy,
      Instant recordedAt) {
    this.equipmentId = equipmentId;
    this.faultId = faultId;
    this.type = type;
    this.performedOn = performedOn;
    this.performedBy = performedBy;
    this.description = description;
    this.partsReplaced = partsReplaced;
    this.cost = cost;
    this.result = result;
    this.recordedBy = recordedBy;
    this.recordedAt = recordedAt;
  }
}
