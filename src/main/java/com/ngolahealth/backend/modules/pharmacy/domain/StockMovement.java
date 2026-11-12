package com.ngolahealth.backend.modules.pharmacy.domain;

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

/** Linha do livro de movimentos. Imutável. */
@Entity
@Immutable
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement extends HospitalScopedEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "medication_id", nullable = false, length = 36)
  private UUID medicationId;

  @Column(name = "batch_id", nullable = false, length = 36)
  private UUID batchId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private MovementType type;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "balance_after", nullable = false)
  private int balanceAfter;

  @Column(name = "patient_id", length = 36)
  private UUID patientId;

  @Column(name = "episode_id", length = 36)
  private UUID episodeId;

  @Column(name = "reference_id", length = 36)
  private UUID referenceId;

  @Column(length = 500)
  private String reason;

  @Column(name = "performed_by", nullable = false, length = 36)
  private UUID performedBy;

  @Column(name = "performed_at", nullable = false)
  private Instant performedAt;

  /** Só o StockService cria movimentos, sempre em conjunto com a alteração do saldo do lote. */
  StockMovement(
      StockBatch batch,
      MovementType type,
      int quantity,
      int balanceAfter,
      MovementContext context,
      UUID performedBy,
      Instant performedAt) {
    this.id = UUID.randomUUID();
    this.medicationId = batch.getMedicationId();
    this.batchId = batch.getId();
    this.type = type;
    this.quantity = quantity;
    this.balanceAfter = balanceAfter;
    this.patientId = context.patientId();
    this.episodeId = context.episodeId();
    this.referenceId = context.referenceId();
    this.reason = context.reason();
    this.performedBy = performedBy;
    this.performedAt = performedAt;
  }
}
