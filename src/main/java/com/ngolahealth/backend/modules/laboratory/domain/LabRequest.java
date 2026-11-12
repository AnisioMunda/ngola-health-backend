package com.ngolahealth.backend.modules.laboratory.domain;

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

/** Pedido de exames (agregado com os seus itens). As regras do fluxo estão aqui. */
@Entity
@Table(name = "lab_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabRequest extends HospitalScopedEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(nullable = false, updatable = false, length = 20)
  private String number;

  @Column(name = "patient_id", nullable = false, updatable = false, length = 36)
  private UUID patientId;

  @Column(name = "episode_id", nullable = false, updatable = false, length = 36)
  private UUID episodeId;

  @Column(name = "requested_by", nullable = false, updatable = false, length = 36)
  private UUID requestedBy;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 10, updatable = false)
  private LabPriority priority;

  @Column(name = "clinical_info", columnDefinition = "text", updatable = false)
  private String clinicalInfo;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 15)
  private LabRequestStatus status = LabRequestStatus.PENDING;

  @Column(name = "collected_at")
  private Instant collectedAt;

  @Column(name = "collected_by", length = 36)
  private UUID collectedBy;

  @Column(name = "collection_notes", length = 500)
  private String collectionNotes;

  @Column(name = "analysis_started_at")
  private Instant analysisStartedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancelled_by", length = 36)
  private UUID cancelledBy;

  @Column(name = "cancellation_reason", length = 500)
  private String cancellationReason;

  @OneToMany(mappedBy = "request", cascade = CascadeType.PERSIST)
  @OrderBy("testName")
  private List<LabRequestItem> items = new ArrayList<>();

  /** Objecto: null = novo (o id é atribuído pela aplicação; ver Prescription). */
  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public LabRequest(
      String number,
      UUID patientId,
      UUID episodeId,
      UUID requestedBy,
      Instant requestedAt,
      LabPriority priority,
      String clinicalInfo,
      List<LabTest> tests) {
    if (tests.isEmpty()) {
      throw new BadRequestException("LAB_REQUEST_EMPTY", "Seleccione pelo menos um exame.");
    }
    var seen = new HashSet<UUID>();
    for (var t : tests) {
      if (!seen.add(t.getId())) {
        throw new BadRequestException(
            "DUPLICATE_TEST", "O exame " + t.getName() + " está repetido.");
      }
    }
    this.id = UUID.randomUUID();
    this.number = number;
    this.patientId = patientId;
    this.episodeId = episodeId;
    this.requestedBy = requestedBy;
    this.requestedAt = requestedAt;
    this.priority = priority;
    this.clinicalInfo = clinicalInfo;
    tests.forEach(t -> items.add(new LabRequestItem(this, t)));
  }

  public void collect(UUID by, Instant at, String notes) {
    requireStatus(LabRequestStatus.PENDING, "A amostra só se regista num pedido pendente.");
    this.status = LabRequestStatus.COLLECTED;
    this.collectedAt = at;
    this.collectedBy = by;
    this.collectionNotes = notes;
  }

  public void startAnalysis(Instant at) {
    requireStatus(LabRequestStatus.COLLECTED, "Só se inicia a análise depois da colheita.");
    this.status = LabRequestStatus.IN_ANALYSIS;
    this.analysisStartedAt = at;
  }

  public LabRequestItem recordResult(UUID itemId, ResultValue value, UUID by, Instant at) {
    requireStatus(
        LabRequestStatus.IN_ANALYSIS, "Os resultados registam-se com o pedido em análise.");
    var item = item(itemId);
    item.recordResult(value, by, at);
    return item;
  }

  /** Valida um resultado; quando todos os exames estão validados, o pedido conclui-se. */
  public LabRequestItem validate(UUID itemId, UUID by, Instant at) {
    requireStatus(
        LabRequestStatus.IN_ANALYSIS, "Só se validam resultados com o pedido em análise.");
    var item = item(itemId);
    item.validate(by, at);
    boolean done =
        items.stream()
            .allMatch(
                i ->
                    i.getStatus() == ItemStatus.VALIDATED || i.getStatus() == ItemStatus.CANCELLED);
    if (done) {
      this.status = LabRequestStatus.COMPLETED;
      this.completedAt = at;
    }
    return item;
  }

  public ResultAmendment amend(UUID itemId, ResultValue value, String reason, UUID by, Instant at) {
    if (status == LabRequestStatus.CANCELLED) {
      throw new ConflictException("LAB_REQUEST_CANCELLED", "O pedido foi cancelado.");
    }
    return item(itemId).amend(value, reason, by, at);
  }

  /** Cancela o que não está validado; resultados já validados mantêm-se. */
  public void cancel(String reason, UUID by, Instant at) {
    if (status == LabRequestStatus.COMPLETED || status == LabRequestStatus.CANCELLED) {
      throw new ConflictException(
          "LAB_REQUEST_CLOSED",
          "O pedido já está "
              + (status == LabRequestStatus.COMPLETED ? "concluído." : "cancelado."));
    }
    items.forEach(LabRequestItem::cancel);
    this.status = LabRequestStatus.CANCELLED;
    this.cancellationReason = reason;
    this.cancelledBy = by;
    this.cancelledAt = at;
  }

  public LabRequestItem item(UUID itemId) {
    return items.stream()
        .filter(i -> i.getId().equals(itemId))
        .findFirst()
        .orElseThrow(
            () ->
                new BadRequestException(
                    "ITEM_NOT_IN_REQUEST", "O exame indicado não pertence a este pedido."));
  }

  private void requireStatus(LabRequestStatus expected, String message) {
    if (status == LabRequestStatus.CANCELLED) {
      throw new ConflictException("LAB_REQUEST_CANCELLED", "O pedido foi cancelado.");
    }
    if (status != expected) {
      throw new ConflictException("INVALID_LAB_STATUS", message);
    }
  }
}
