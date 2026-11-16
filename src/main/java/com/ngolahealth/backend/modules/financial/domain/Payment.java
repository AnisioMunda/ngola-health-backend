package com.ngolahealth.backend.modules.financial.domain;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Pagamento (imutável) com o número do seu recibo. */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "invoice_id", nullable = false, updatable = false, length = 36)
  private UUID invoiceId;

  @Column(name = "receipt_number", nullable = false, updatable = false, length = 30)
  private String receiptNumber;

  @Column(nullable = false, updatable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, updatable = false, length = 20)
  private PaymentMethod method;

  @Column(updatable = false, length = 100)
  private String reference;

  @Column(name = "received_by", nullable = false, updatable = false, length = 36)
  private UUID receivedBy;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  public Payment(
      UUID invoiceId,
      String receiptNumber,
      BigDecimal amount,
      PaymentMethod method,
      String reference,
      UUID receivedBy,
      Instant receivedAt) {
    this.invoiceId = invoiceId;
    this.receiptNumber = receiptNumber;
    this.amount = Money.round(amount);
    this.method = method;
    this.reference = reference;
    this.receivedBy = receivedBy;
    this.receivedAt = receivedAt;
  }
}
