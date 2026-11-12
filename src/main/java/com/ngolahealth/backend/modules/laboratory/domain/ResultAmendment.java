package com.ngolahealth.backend.modules.laboratory.domain;

import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Correcção de um resultado validado: valor anterior e novo, motivo, quem e quando. */
@Entity
@Table(name = "lab_result_amendments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResultAmendment extends HospitalScopedEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "item_id", nullable = false, updatable = false, length = 36)
  private UUID itemId;

  @Column(name = "previous_numeric", precision = 12, scale = 4, updatable = false)
  private BigDecimal previousNumeric;

  @Column(name = "previous_text", length = 500, updatable = false)
  private String previousText;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "previous_flag", length = 10, updatable = false)
  private ResultFlag previousFlag;

  @Column(name = "new_numeric", precision = 12, scale = 4, updatable = false)
  private BigDecimal newNumeric;

  @Column(name = "new_text", length = 500, updatable = false)
  private String newText;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "new_flag", length = 10, updatable = false)
  private ResultFlag newFlag;

  @Column(nullable = false, length = 500, updatable = false)
  private String reason;

  @Column(name = "amended_by", nullable = false, length = 36, updatable = false)
  private UUID amendedBy;

  @Column(name = "amended_at", nullable = false, updatable = false)
  private Instant amendedAt;

  ResultAmendment(
      LabRequestItem item,
      BigDecimal previousNumeric,
      String previousText,
      ResultFlag previousFlag,
      String reason,
      UUID by,
      Instant at) {
    this.id = UUID.randomUUID();
    this.itemId = item.getId();
    this.previousNumeric = previousNumeric;
    this.previousText = previousText;
    this.previousFlag = previousFlag;
    this.reason = reason;
    this.amendedBy = by;
    this.amendedAt = at;
  }

  void recordNew(BigDecimal numeric, String text, ResultFlag flag) {
    this.newNumeric = numeric;
    this.newText = text;
    this.newFlag = flag;
  }
}
