package com.ngolahealth.backend.modules.laboratory.domain;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Um exame de um pedido, com o seu resultado. */
@Entity
@Table(name = "lab_request_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabRequestItem extends HospitalScopedEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "request_id", nullable = false, updatable = false)
  private LabRequest request;

  @Column(name = "lab_test_id", nullable = false, updatable = false, length = 36)
  private UUID labTestId;

  @Column(name = "test_code", nullable = false, updatable = false, length = 30)
  private String testCode;

  @Column(name = "test_name", nullable = false, updatable = false, length = 200)
  private String testName;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "result_type", nullable = false, updatable = false, length = 15)
  private ResultType resultType;

  @Column(length = 30, updatable = false)
  private String unit;

  @Column(name = "reference_low", precision = 12, scale = 4, updatable = false)
  private BigDecimal referenceLow;

  @Column(name = "reference_high", precision = 12, scale = 4, updatable = false)
  private BigDecimal referenceHigh;

  @Column(name = "reference_text", length = 200, updatable = false)
  private String referenceText;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 15)
  private ItemStatus status = ItemStatus.PENDING;

  @Column(name = "numeric_value", precision = 12, scale = 4)
  private BigDecimal numericValue;

  @Column(name = "text_value", length = 500)
  private String textValue;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(length = 10)
  private ResultFlag flag;

  @Column(name = "result_notes", length = 1000)
  private String resultNotes;

  @Column(name = "resulted_at")
  private Instant resultedAt;

  @Column(name = "resulted_by", length = 36)
  private UUID resultedBy;

  @Column(name = "validated_at")
  private Instant validatedAt;

  @Column(name = "validated_by", length = 36)
  private UUID validatedBy;

  @Column(nullable = false)
  private boolean amended;

  /** Copia do catálogo o nome e os valores de referência em vigor no momento do pedido. */
  LabRequestItem(LabRequest request, LabTest test) {
    this.id = UUID.randomUUID();
    this.request = request;
    this.labTestId = test.getId();
    this.testCode = test.getCode();
    this.testName = test.getName();
    this.resultType = test.getResultType();
    this.unit = test.getUnit();
    this.referenceLow = test.getReferenceLow();
    this.referenceHigh = test.getReferenceHigh();
    this.referenceText = test.getReferenceText();
  }

  /** Regista (ou substitui, antes de validado) o resultado. */
  void recordResult(ResultValue value, UUID by, Instant at) {
    if (status == ItemStatus.CANCELLED) {
      throw new ConflictException("ITEM_CANCELLED", "O exame " + testName + " foi cancelado.");
    }
    if (status == ItemStatus.VALIDATED) {
      throw new ConflictException(
          "RESULT_VALIDATED",
          "O resultado de " + testName + " já foi validado; use a correcção com motivo.");
    }
    applyValue(value);
    this.status = ItemStatus.RESULTED;
    this.resultedAt = at;
    this.resultedBy = by;
  }

  void validate(UUID by, Instant at) {
    if (status != ItemStatus.RESULTED) {
      throw new ConflictException(
          "RESULT_NOT_READY", "O exame " + testName + " não tem resultado por validar.");
    }
    this.status = ItemStatus.VALIDATED;
    this.validatedAt = at;
    this.validatedBy = by;
  }

  /** Corrige um resultado validado; devolve a correcção para o histórico. */
  ResultAmendment amend(ResultValue value, String reason, UUID by, Instant at) {
    if (status != ItemStatus.VALIDATED) {
      throw new ConflictException(
          "RESULT_NOT_VALIDATED", "Só se corrige um resultado já validado; antes disso, edite-o.");
    }
    var amendment = new ResultAmendment(this, numericValue, textValue, flag, reason, by, at);
    applyValue(value);
    amendment.recordNew(numericValue, textValue, flag);
    this.amended = true;
    this.validatedAt = at;
    this.validatedBy = by;
    return amendment;
  }

  void cancel() {
    if (status == ItemStatus.PENDING || status == ItemStatus.RESULTED) {
      this.status = ItemStatus.CANCELLED;
    }
  }

  private void applyValue(ResultValue value) {
    if (resultType == ResultType.NUMERIC) {
      if (value.numericValue() == null) {
        throw new BadRequestException(
            "NUMERIC_RESULT_REQUIRED", "Indique o valor numérico de " + testName + ".");
      }
      this.numericValue = value.numericValue();
      this.textValue = null;
      this.flag = flagFor(value.numericValue());
    } else {
      if (value.textValue() == null || value.textValue().isBlank()) {
        throw new BadRequestException(
            "TEXT_RESULT_REQUIRED", "Indique o resultado de " + testName + ".");
      }
      this.textValue = value.textValue().strip();
      this.numericValue = null;
      this.flag = value.abnormal() ? ResultFlag.ABNORMAL : ResultFlag.NORMAL;
    }
    this.resultNotes = value.notes();
  }

  /** Sinalização automática pelos valores de referência copiados no pedido. */
  ResultFlag flagFor(BigDecimal value) {
    if (referenceLow == null && referenceHigh == null) {
      return null;
    }
    if (referenceLow != null && value.compareTo(referenceLow) < 0) {
      return ResultFlag.LOW;
    }
    if (referenceHigh != null && value.compareTo(referenceHigh) > 0) {
      return ResultFlag.HIGH;
    }
    return ResultFlag.NORMAL;
  }
}
