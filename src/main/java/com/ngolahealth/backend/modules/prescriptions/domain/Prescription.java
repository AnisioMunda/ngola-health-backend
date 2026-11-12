package com.ngolahealth.backend.modules.prescriptions.domain;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Prescrição: agregado com as suas linhas. As regras de dispensa e cancelamento estão aqui; o
 * conteúdo clínico (linhas) não se altera depois de emitida — corrige-se cancelando e emitindo
 * outra.
 */
@Entity
@Table(name = "prescriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription extends HospitalScopedEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(nullable = false, updatable = false, length = 20)
  private String number;

  @Column(name = "patient_id", nullable = false, updatable = false, length = 36)
  private UUID patientId;

  @Column(name = "episode_id", nullable = false, updatable = false, length = 36)
  private UUID episodeId;

  @Column(name = "prescriber_id", nullable = false, updatable = false, length = 36)
  private UUID prescriberId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 25)
  private PrescriptionStatus status = PrescriptionStatus.ACTIVE;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private Instant issuedAt;

  @Column(name = "valid_until", nullable = false, updatable = false)
  private LocalDate validUntil;

  @Column(columnDefinition = "text", updatable = false)
  private String notes;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancelled_by", length = 36)
  private UUID cancelledBy;

  @Column(name = "cancellation_reason", length = 500)
  private String cancellationReason;

  @OneToMany(mappedBy = "prescription", cascade = CascadeType.PERSIST)
  @OrderBy("lineNumber")
  private List<PrescriptionItem> items = new ArrayList<>();

  /**
   * Objecto (não primitivo): null indica ao Spring Data que a prescrição é nova e deve ser inserida
   * (persist, com as linhas em cascata) — o id é atribuído pela aplicação.
   */
  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Prescription(
      String number,
      UUID patientId,
      UUID episodeId,
      UUID prescriberId,
      Instant issuedAt,
      LocalDate validUntil,
      String notes,
      List<ItemDetails> lines) {
    if (lines.isEmpty()) {
      throw new BadRequestException(
          "PRESCRIPTION_EMPTY", "A prescrição tem de ter pelo menos um medicamento.");
    }
    var seen = new HashSet<UUID>();
    for (var line : lines) {
      if (!seen.add(line.medicationId())) {
        throw new BadRequestException(
            "DUPLICATE_MEDICATION",
            "O mesmo medicamento aparece em mais do que uma linha; junte as quantidades.");
      }
    }
    this.id = UUID.randomUUID();
    this.number = number;
    this.patientId = patientId;
    this.episodeId = episodeId;
    this.prescriberId = prescriberId;
    this.issuedAt = issuedAt;
    this.validUntil = validUntil;
    this.notes = notes;
    for (int i = 0; i < lines.size(); i++) {
      items.add(new PrescriptionItem(this, i + 1, lines.get(i)));
    }
  }

  public boolean isExpired(LocalDate today) {
    return status.isOpen() && validUntil.isBefore(today);
  }

  public PrescriptionItem item(UUID itemId) {
    return items.stream()
        .filter(i -> i.getId().equals(itemId))
        .findFirst()
        .orElseThrow(
            () ->
                new BadRequestException(
                    "ITEM_NOT_IN_PRESCRIPTION",
                    "A linha indicada não pertence a esta prescrição."));
  }

  /** Verifica se ainda se pode dispensar (chamar antes de mexer no stock). */
  public void ensureDispensable(LocalDate today) {
    if (status == PrescriptionStatus.CANCELLED) {
      throw new ConflictException("PRESCRIPTION_CANCELLED", "A prescrição foi cancelada.");
    }
    if (status == PrescriptionStatus.DISPENSED) {
      throw new ConflictException(
          "PRESCRIPTION_DISPENSED", "A prescrição já foi totalmente dispensada.");
    }
    if (isExpired(today)) {
      throw new ConflictException(
          "PRESCRIPTION_EXPIRED", "A prescrição caducou em " + validUntil + ".");
    }
  }

  /** Regista unidades dispensadas numa linha e actualiza o estado. */
  public void recordDispensed(UUID itemId, int units, LocalDate today) {
    ensureDispensable(today);
    item(itemId).dispense(units);
    boolean all = items.stream().allMatch(i -> i.remaining() == 0);
    this.status = all ? PrescriptionStatus.DISPENSED : PrescriptionStatus.PARTIALLY_DISPENSED;
  }

  /** Cancela o que falta dispensar; o já dispensado mantém-se registado. */
  public void cancel(String reason, UUID by, Instant at) {
    if (!status.isOpen()) {
      throw new ConflictException(
          "PRESCRIPTION_NOT_OPEN",
          status == PrescriptionStatus.CANCELLED
              ? "A prescrição já está cancelada."
              : "Não é possível cancelar uma prescrição totalmente dispensada.");
    }
    this.status = PrescriptionStatus.CANCELLED;
    this.cancellationReason = reason;
    this.cancelledBy = by;
    this.cancelledAt = at;
  }
}
