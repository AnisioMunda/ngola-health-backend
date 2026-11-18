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
import java.math.BigDecimal;
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

/**
 * Equipamento. O estado só muda pelo fluxo: avaria (fora de serviço), envio para manutenção,
 * registo de manutenção (volta a operacional se passar e não houver avarias abertas) e abate.
 */
@Entity
@Table(name = "equipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private EquipmentCategory category;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 10)
  private Criticality criticality;

  @Column(length = 100)
  private String brand;

  @Column(length = 100)
  private String model;

  @Column(name = "serial_number", length = 100)
  private String serialNumber;

  @Column(name = "ward_id", length = 36)
  private UUID wardId;

  @Column(length = 200)
  private String location;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private EquipmentStatus status = EquipmentStatus.OPERATIONAL;

  @Column(name = "purchase_date")
  private LocalDate purchaseDate;

  @Column(name = "purchase_price", precision = 14, scale = 2)
  private BigDecimal purchasePrice;

  @Column(name = "warranty_until")
  private LocalDate warrantyUntil;

  @Column(name = "maintenance_interval_days")
  private Integer maintenanceIntervalDays;

  @Column(name = "next_maintenance_date")
  private LocalDate nextMaintenanceDate;

  @Column(name = "calibration_interval_days")
  private Integer calibrationIntervalDays;

  @Column(name = "next_calibration_date")
  private LocalDate nextCalibrationDate;

  @Column(length = 1000)
  private String notes;

  @Column(name = "retired_at")
  private Instant retiredAt;

  @Column(name = "retired_by", length = 36)
  private UUID retiredBy;

  @Column(name = "retirement_reason", length = 300)
  private String retirementReason;

  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Equipment(EquipmentDetails d) {
    apply(d);
  }

  public void apply(EquipmentDetails d) {
    requireNotRetired();
    this.code = d.code();
    this.name = d.name();
    this.category = d.category();
    this.criticality = d.criticality();
    this.brand = d.brand();
    this.model = d.model();
    this.serialNumber = d.serialNumber();
    this.wardId = d.wardId();
    this.location = d.location();
    this.purchaseDate = d.purchaseDate();
    this.purchasePrice = d.purchasePrice();
    this.warrantyUntil = d.warrantyUntil();
    this.maintenanceIntervalDays = d.maintenanceIntervalDays();
    this.nextMaintenanceDate = d.nextMaintenanceDate();
    this.calibrationIntervalDays = d.calibrationIntervalDays();
    this.nextCalibrationDate = d.nextCalibrationDate();
    this.notes = d.notes();
  }

  public boolean isUsable() {
    return status == EquipmentStatus.OPERATIONAL;
  }

  /** Avaria que impede o uso. */
  public void putOutOfService() {
    requireNotRetired();
    if (status == EquipmentStatus.OPERATIONAL) {
      this.status = EquipmentStatus.OUT_OF_SERVICE;
    }
  }

  public void sendToMaintenance() {
    requireNotRetired();
    if (status == EquipmentStatus.UNDER_MAINTENANCE) {
      throw new ConflictException("ALREADY_IN_MAINTENANCE", "O equipamento já está em manutenção.");
    }
    this.status = EquipmentStatus.UNDER_MAINTENANCE;
  }

  /**
   * Efeito de uma manutenção registada: actualiza as próximas datas e o estado. Só volta a
   * operacional se passou e não restam avarias que o impeçam.
   */
  public void applyMaintenance(
      MaintenanceType type,
      LocalDate performedOn,
      MaintenanceResult result,
      boolean blockingFaultsRemain) {
    requireNotRetired();
    if ((type == MaintenanceType.PREVENTIVE || type == MaintenanceType.INSPECTION)
        && maintenanceIntervalDays != null) {
      this.nextMaintenanceDate = performedOn.plusDays(maintenanceIntervalDays);
    }
    if (type == MaintenanceType.CALIBRATION && calibrationIntervalDays != null) {
      this.nextCalibrationDate = performedOn.plusDays(calibrationIntervalDays);
    }
    if (result == MaintenanceResult.FAILED) {
      this.status = EquipmentStatus.OUT_OF_SERVICE;
    } else {
      this.status =
          blockingFaultsRemain ? EquipmentStatus.OUT_OF_SERVICE : EquipmentStatus.OPERATIONAL;
    }
  }

  /** Repõe em serviço depois de uma avaria descartada (sem outras pendentes). */
  public void backInService() {
    requireNotRetired();
    if (status == EquipmentStatus.OUT_OF_SERVICE) {
      this.status = EquipmentStatus.OPERATIONAL;
    }
  }

  public void retire(String reason, UUID by, Instant at) {
    requireNotRetired();
    this.status = EquipmentStatus.RETIRED;
    this.retirementReason = reason;
    this.retiredBy = by;
    this.retiredAt = at;
  }

  public void requireNotRetired() {
    if (status == EquipmentStatus.RETIRED) {
      throw new ConflictException("EQUIPMENT_RETIRED", "O equipamento foi abatido.");
    }
  }
}
