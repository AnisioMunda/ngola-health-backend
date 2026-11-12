package com.ngolahealth.backend.modules.pharmacy.domain;

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

/** Medicamento do catálogo do hospital. Nunca é apagado (histórico de movimentos). */
@Entity
@Table(name = "medications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medication extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "generic_name", length = 200)
  private String genericName;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "dosage_form", nullable = false, length = 20)
  private DosageForm dosageForm;

  @Column(length = 50)
  private String strength;

  @Column(name = "dispensing_unit", nullable = false, length = 30)
  private String dispensingUnit;

  @Column(name = "requires_prescription", nullable = false)
  private boolean requiresPrescription;

  @Column(name = "min_stock_level", nullable = false)
  private int minStockLevel;

  @Column(nullable = false)
  private boolean active = true;

  @Version
  @Column(nullable = false)
  private long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Medication(MedicationDetails details) {
    apply(details);
  }

  public void apply(MedicationDetails d) {
    this.name = d.name();
    this.genericName = d.genericName();
    this.dosageForm = d.dosageForm();
    this.strength = d.strength();
    this.dispensingUnit = d.dispensingUnit();
    this.requiresPrescription = d.requiresPrescription();
    this.minStockLevel = d.minStockLevel();
  }

  public void activate() {
    this.active = true;
  }

  public void deactivate() {
    this.active = false;
  }
}
