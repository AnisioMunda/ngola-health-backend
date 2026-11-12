package com.ngolahealth.backend.modules.prescriptions.domain;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Linha de uma prescrição: um medicamento, quanto e como tomar. */
@Entity
@Table(name = "prescription_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrescriptionItem extends HospitalScopedEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "prescription_id", nullable = false, updatable = false)
  private Prescription prescription;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Column(name = "medication_id", nullable = false, updatable = false, length = 36)
  private UUID medicationId;

  @Column(nullable = false, updatable = false)
  private int quantity;

  @Column(name = "quantity_dispensed", nullable = false)
  private int quantityDispensed;

  @Column(nullable = false, length = 200, updatable = false)
  private String dosage;

  @Column(nullable = false, length = 100, updatable = false)
  private String frequency;

  @Column(name = "duration_days", updatable = false)
  private Integer durationDays;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20, updatable = false)
  private Route route;

  @Column(length = 500, updatable = false)
  private String instructions;

  PrescriptionItem(Prescription prescription, int lineNumber, ItemDetails d) {
    this.id = UUID.randomUUID();
    this.prescription = prescription;
    this.lineNumber = lineNumber;
    this.medicationId = d.medicationId();
    this.quantity = d.quantity();
    this.dosage = d.dosage();
    this.frequency = d.frequency();
    this.durationDays = d.durationDays();
    this.route = d.route();
    this.instructions = d.instructions();
  }

  public int remaining() {
    return quantity - quantityDispensed;
  }

  void dispense(int units) {
    if (units > remaining()) {
      throw new BadRequestException(
          "QUANTITY_EXCEEDS_PRESCRIBED",
          "Linha "
              + lineNumber
              + ": só faltam dispensar "
              + remaining()
              + " unidade(s) das "
              + quantity
              + " prescritas.");
    }
    this.quantityDispensed += units;
  }
}
