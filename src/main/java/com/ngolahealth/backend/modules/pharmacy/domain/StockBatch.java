package com.ngolahealth.backend.modules.pharmacy.domain;

import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Lote de um medicamento. O saldo só muda através de movimentos (ver StockService). */
@Entity
@Table(name = "stock_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockBatch extends HospitalScopedEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "medication_id", nullable = false, updatable = false, length = 36)
  private UUID medicationId;

  @Column(name = "batch_number", nullable = false, updatable = false, length = 50)
  private String batchNumber;

  @Column(name = "expiry_date", nullable = false, updatable = false)
  private LocalDate expiryDate;

  @Column(name = "quantity_received", nullable = false, updatable = false)
  private int quantityReceived;

  @Column(name = "quantity_available", nullable = false)
  private int quantityAvailable;

  @Column(name = "unit_cost", precision = 12, scale = 2, updatable = false)
  private BigDecimal unitCost;

  @Column(length = 200, updatable = false)
  private String supplier;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  @Column(name = "received_by", nullable = false, updatable = false, length = 36)
  private UUID receivedBy;

  @Version
  @Column(nullable = false)
  private long version;

  public StockBatch(
      UUID medicationId,
      String batchNumber,
      LocalDate expiryDate,
      int quantity,
      BigDecimal unitCost,
      String supplier,
      UUID receivedBy,
      Instant receivedAt) {
    this.id = UUID.randomUUID();
    this.medicationId = medicationId;
    this.batchNumber = batchNumber;
    this.expiryDate = expiryDate;
    this.quantityReceived = quantity;
    this.quantityAvailable = quantity;
    this.unitCost = unitCost;
    this.supplier = supplier;
    this.receivedBy = receivedBy;
    this.receivedAt = receivedAt;
  }

  public boolean isExpired(LocalDate today) {
    return expiryDate.isBefore(today);
  }

  /** Aplica uma variação ao saldo; nunca o deixa negativo. Devolve o novo saldo. */
  int change(int delta) {
    int next = quantityAvailable + delta;
    if (next < 0) {
      throw new ConflictException(
          "INSUFFICIENT_BATCH_STOCK",
          "O lote " + batchNumber + " só tem " + quantityAvailable + " unidade(s).");
    }
    this.quantityAvailable = next;
    return next;
  }
}
