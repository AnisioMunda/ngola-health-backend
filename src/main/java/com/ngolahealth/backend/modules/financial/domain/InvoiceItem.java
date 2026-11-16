package com.ngolahealth.backend.modules.financial.domain;

import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Linha de um documento, com os valores já calculados (arredondados linha a linha). */
@Entity
@Table(name = "invoice_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvoiceItem extends HospitalScopedEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
  private Invoice invoice;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Column(name = "service_price_id", updatable = false, length = 36)
  private UUID servicePriceId;

  @Column(updatable = false, length = 30)
  private String code;

  @Column(nullable = false, updatable = false, length = 300)
  private String description;

  @Column(nullable = false, updatable = false, precision = 10, scale = 2)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false, updatable = false, precision = 14, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "discount_percent", nullable = false, updatable = false, precision = 5, scale = 2)
  private BigDecimal discountPercent;

  @Column(name = "vat_rate", nullable = false, updatable = false, precision = 5, scale = 2)
  private BigDecimal vatRate;

  @Column(name = "vat_exemption_reason", updatable = false, length = 200)
  private String vatExemptionReason;

  @Column(name = "discount_amount", nullable = false, updatable = false, precision = 14, scale = 2)
  private BigDecimal discountAmount;

  @Column(name = "net_amount", nullable = false, updatable = false, precision = 14, scale = 2)
  private BigDecimal netAmount;

  @Column(name = "vat_amount", nullable = false, updatable = false, precision = 14, scale = 2)
  private BigDecimal vatAmount;

  @Column(name = "gross_amount", nullable = false, updatable = false, precision = 14, scale = 2)
  private BigDecimal grossAmount;

  InvoiceItem(Invoice invoice, int lineNumber, LineInput in) {
    this.id = UUID.randomUUID();
    this.invoice = invoice;
    this.lineNumber = lineNumber;
    this.servicePriceId = in.servicePriceId();
    this.code = in.code();
    this.description = in.description();
    this.quantity = in.quantity();
    this.unitPrice = Money.round(in.unitPrice());
    this.discountPercent = in.discountPercent();
    this.vatRate = in.vatRate();
    this.vatExemptionReason = in.vatExemptionReason();
    var base = Money.round(quantity.multiply(unitPrice));
    this.discountAmount = Money.percentOf(base, discountPercent);
    this.netAmount = Money.round(base.subtract(discountAmount));
    this.vatAmount = Money.percentOf(netAmount, vatRate);
    this.grossAmount = netAmount.add(vatAmount);
  }

  /** Cópia exacta para a nota de crédito. */
  InvoiceItem copyFor(Invoice creditNote) {
    return new InvoiceItem(
        creditNote,
        lineNumber,
        new LineInput(
            servicePriceId,
            code,
            description,
            quantity,
            unitPrice,
            discountPercent,
            vatRate,
            vatExemptionReason));
  }
}
