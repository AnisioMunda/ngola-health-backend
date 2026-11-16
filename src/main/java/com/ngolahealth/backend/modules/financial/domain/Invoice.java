package com.ngolahealth.backend.modules.financial.domain;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Documento de facturação. Só o rascunho se edita; a emissão atribui o número e congela o conteúdo.
 * Depois disso só mudam os pagamentos e o estado (anulação ou nota de crédito).
 */
@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice extends HospitalScopedEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "document_type", nullable = false, length = 2)
  private DocumentType documentType;

  @Column(name = "series_year")
  private Integer seriesYear;

  @Column(name = "sequence_number")
  private Long sequenceNumber;

  @Column(length = 30)
  private String number;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private InvoiceStatus status = InvoiceStatus.DRAFT;

  @Column(name = "patient_id", nullable = false, updatable = false, length = 36)
  private UUID patientId;

  @Column(name = "episode_id", length = 36)
  private UUID episodeId;

  @Column(name = "customer_name", nullable = false, length = 200)
  private String customerName;

  @Column(name = "customer_tax_id", length = 20)
  private String customerTaxId;

  @Column(name = "customer_address", length = 300)
  private String customerAddress;

  @Column(name = "issuer_name", length = 200)
  private String issuerName;

  @Column(name = "issuer_tax_id", length = 20)
  private String issuerTaxId;

  @Column(name = "issuer_address", length = 300)
  private String issuerAddress;

  @Column(nullable = false, length = 3, updatable = false)
  private String currency = Money.CURRENCY;

  @Column(name = "net_total", nullable = false, precision = 14, scale = 2)
  private BigDecimal netTotal = Money.zero();

  @Column(name = "vat_total", nullable = false, precision = 14, scale = 2)
  private BigDecimal vatTotal = Money.zero();

  @Column(name = "gross_total", nullable = false, precision = 14, scale = 2)
  private BigDecimal grossTotal = Money.zero();

  @Column(name = "paid_total", nullable = false, precision = 14, scale = 2)
  private BigDecimal paidTotal = Money.zero();

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(length = 1000)
  private String notes;

  @Column(name = "related_invoice_id", updatable = false, length = 36)
  private UUID relatedInvoiceId;

  @Column(name = "credit_reason", updatable = false, length = 500)
  private String creditReason;

  @Column(name = "created_by", nullable = false, updatable = false, length = 36)
  private UUID createdBy;

  @Column(name = "issued_by", length = 36)
  private UUID issuedBy;

  @Column(name = "issued_at")
  private Instant issuedAt;

  @Column(name = "cancelled_by", length = 36)
  private UUID cancelledBy;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancellation_reason", length = 500)
  private String cancellationReason;

  @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("lineNumber")
  private List<InvoiceItem> items = new ArrayList<>();

  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Invoice(
      DocumentType type,
      UUID patientId,
      UUID episodeId,
      Customer customer,
      LocalDate dueDate,
      String notes,
      List<LineInput> lines,
      UUID createdBy) {
    if (type == DocumentType.NC) {
      throw new IllegalArgumentException("Notas de crédito criam-se a partir do documento");
    }
    this.id = UUID.randomUUID();
    this.documentType = type;
    this.patientId = patientId;
    this.createdBy = createdBy;
    edit(episodeId, customer, dueDate, notes, lines);
  }

  /** Nota de crédito (já com as linhas do original); a emissão atribui-lhe o número. */
  public static Invoice creditNoteFor(Invoice original, String reason, UUID by) {
    original.requireCreditable();
    var nc = new Invoice();
    nc.id = UUID.randomUUID();
    nc.documentType = DocumentType.NC;
    nc.patientId = original.patientId;
    nc.episodeId = original.episodeId;
    nc.customerName = original.customerName;
    nc.customerTaxId = original.customerTaxId;
    nc.customerAddress = original.customerAddress;
    nc.relatedInvoiceId = original.id;
    nc.creditReason = reason;
    nc.createdBy = by;
    original.items.forEach(i -> nc.items.add(i.copyFor(nc)));
    nc.recompute();
    return nc;
  }

  public BigDecimal balance() {
    return grossTotal.subtract(paidTotal);
  }

  public void edit(
      UUID episodeId, Customer customer, LocalDate dueDate, String notes, List<LineInput> lines) {
    requireDraft();
    if (documentType == DocumentType.FR && dueDate != null) {
      throw new BadRequestException(
          "DUE_DATE_NOT_ALLOWED", "A factura-recibo é paga na emissão: não tem vencimento.");
    }
    this.episodeId = episodeId;
    this.customerName = customer.name();
    this.customerTaxId = customer.taxId();
    this.customerAddress = customer.address();
    this.dueDate = dueDate;
    this.notes = notes;
    items.clear();
    for (int i = 0; i < lines.size(); i++) {
      items.add(new InvoiceItem(this, i + 1, lines.get(i)));
    }
    recompute();
  }

  /** Atribui o número e congela o documento. */
  public void issue(int year, long sequence, String number, Issuer issuer, UUID by, Instant at) {
    requireDraft();
    if (items.isEmpty()) {
      throw new BadRequestException("NO_ITEMS", "O documento não tem linhas.");
    }
    if (grossTotal.signum() <= 0) {
      throw new BadRequestException("ZERO_TOTAL", "O documento tem valor zero.");
    }
    items.stream()
        .filter(i -> i.getVatRate().signum() == 0 && blank(i.getVatExemptionReason()))
        .findFirst()
        .ifPresent(
            i -> {
              throw new BadRequestException(
                  "VAT_EXEMPTION_REQUIRED",
                  "A linha \""
                      + i.getDescription()
                      + "\" tem IVA 0%: indique o motivo de isenção.");
            });
    this.seriesYear = year;
    this.sequenceNumber = sequence;
    this.number = number;
    this.issuerName = issuer.name();
    this.issuerTaxId = issuer.taxId();
    this.issuerAddress = issuer.address();
    this.issuedBy = by;
    this.issuedAt = at;
    this.status = InvoiceStatus.ISSUED;
  }

  public void registerPayment(BigDecimal amount) {
    if (documentType == DocumentType.NC) {
      throw new ConflictException("NOT_PAYABLE", "Uma nota de crédito não recebe pagamentos.");
    }
    if (status != InvoiceStatus.ISSUED && status != InvoiceStatus.PARTIALLY_PAID) {
      throw new ConflictException(
          "NOT_PAYABLE",
          status == InvoiceStatus.DRAFT
              ? "Emita o documento antes de registar pagamentos."
              : "O documento não aceita pagamentos (" + status + ").");
    }
    var value = Money.round(amount);
    if (value.signum() <= 0) {
      throw new BadRequestException("INVALID_AMOUNT", "O valor tem de ser positivo.");
    }
    if (value.compareTo(balance()) > 0) {
      throw new BadRequestException(
          "AMOUNT_EXCEEDS_BALANCE", "O valor excede o saldo em dívida (" + balance() + " AOA).");
    }
    this.paidTotal = paidTotal.add(value);
    this.status =
        paidTotal.compareTo(grossTotal) == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID;
  }

  /** Anulação: só de um documento emitido sem pagamentos. Com pagamentos, nota de crédito. */
  public void cancel(String reason, UUID by, Instant at) {
    if (documentType == DocumentType.NC) {
      throw new ConflictException("NOT_CANCELLABLE", "Uma nota de crédito não se anula.");
    }
    if (status != InvoiceStatus.ISSUED || paidTotal.signum() > 0) {
      throw new ConflictException(
          "NOT_CANCELLABLE",
          status == InvoiceStatus.DRAFT
              ? "Um rascunho não se anula: elimine-o."
              : "Só se anula um documento emitido sem pagamentos; emita uma nota de crédito.");
    }
    this.status = InvoiceStatus.CANCELLED;
    this.cancellationReason = reason;
    this.cancelledBy = by;
    this.cancelledAt = at;
  }

  void requireCreditable() {
    if (documentType == DocumentType.NC
        || (status != InvoiceStatus.ISSUED
            && status != InvoiceStatus.PARTIALLY_PAID
            && status != InvoiceStatus.PAID)) {
      throw new ConflictException(
          "NOT_CREDITABLE", "Só se emite nota de crédito de uma factura emitida e não anulada.");
    }
  }

  public void markCredited() {
    requireCreditable();
    this.status = InvoiceStatus.CREDITED;
  }

  public void requireDraft() {
    if (status != InvoiceStatus.DRAFT) {
      throw new ConflictException(
          "DOCUMENT_ISSUED", "O documento já foi emitido e não pode ser alterado.");
    }
  }

  private void recompute() {
    this.netTotal = sum(items.stream().map(InvoiceItem::getNetAmount).toList());
    this.vatTotal = sum(items.stream().map(InvoiceItem::getVatAmount).toList());
    this.grossTotal = netTotal.add(vatTotal);
  }

  private static BigDecimal sum(List<BigDecimal> values) {
    return values.stream().reduce(Money.zero(), BigDecimal::add);
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
