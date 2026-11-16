package com.ngolahealth.backend.modules.financial.api;

import com.ngolahealth.backend.modules.financial.domain.DocumentType;
import com.ngolahealth.backend.modules.financial.domain.InvoiceStatus;
import com.ngolahealth.backend.modules.financial.domain.PaymentMethod;
import com.ngolahealth.backend.modules.financial.domain.ServiceCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FinancialDtos {

  private FinancialDtos() {}

  // ---------- Preços ----------

  public record ServicePriceRequest(
      @NotBlank(message = "Indique o código.")
          @Size(max = 30, message = "O código não pode exceder 30 caracteres.")
          @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Use só letras, números, ., - e _.")
          String code,
      @NotBlank(message = "Indique a descrição.")
          @Size(max = 300, message = "A descrição não pode exceder 300 caracteres.")
          String description,
      @NotNull(message = "Seleccione a categoria.") ServiceCategory category,
      @NotNull(message = "Indique o preço.")
          @DecimalMin(value = "0.00", message = "O preço não pode ser negativo.")
          @Digits(integer = 12, fraction = 2, message = "Preço inválido (máx. 2 casas decimais).")
          BigDecimal unitPrice,
      @NotNull(message = "Indique a taxa de IVA.")
          @DecimalMin(value = "0", message = "Taxa de IVA inválida.")
          @DecimalMax(value = "100", message = "Taxa de IVA inválida.")
          BigDecimal vatRate,
      @Size(max = 200, message = "O motivo de isenção não pode exceder 200 caracteres.")
          String vatExemptionReason,
      Long version) {}

  public record ServicePriceResponse(
      UUID id,
      String code,
      String description,
      ServiceCategory category,
      BigDecimal unitPrice,
      BigDecimal vatRate,
      String vatExemptionReason,
      boolean active,
      long version) {}

  // ---------- Documentos ----------

  /** Linha: do catálogo ({@code servicePriceId}) ou livre (descrição, preço e IVA). */
  public record LineRequest(
      UUID servicePriceId,
      @Size(max = 300, message = "A descrição não pode exceder 300 caracteres.") String description,
      @NotNull(message = "Indique a quantidade.")
          @DecimalMin(value = "0.01", message = "A quantidade tem de ser positiva.")
          @Digits(integer = 8, fraction = 2, message = "Quantidade inválida.")
          BigDecimal quantity,
      @DecimalMin(value = "0.00", message = "O preço não pode ser negativo.")
          @Digits(integer = 12, fraction = 2, message = "Preço inválido (máx. 2 casas decimais).")
          BigDecimal unitPrice,
      @DecimalMin(value = "0", message = "Desconto inválido.")
          @DecimalMax(value = "100", message = "O desconto não pode exceder 100%.")
          BigDecimal discountPercent,
      @DecimalMin(value = "0", message = "Taxa de IVA inválida.")
          @DecimalMax(value = "100", message = "Taxa de IVA inválida.")
          BigDecimal vatRate,
      @Size(max = 200, message = "O motivo de isenção não pode exceder 200 caracteres.")
          String vatExemptionReason) {}

  public record InvoiceRequest(
      @NotNull(message = "Seleccione o tipo de documento.") DocumentType documentType,
      @NotNull(message = "Seleccione o paciente.") UUID patientId,
      UUID episodeId,
      @Size(max = 200, message = "O nome não pode exceder 200 caracteres.") String customerName,
      @Size(max = 20, message = "O NIF não pode exceder 20 caracteres.")
          @Pattern(regexp = "[A-Za-z0-9]*", message = "O NIF só tem letras e números.")
          String customerTaxId,
      @Size(max = 300, message = "A morada não pode exceder 300 caracteres.")
          String customerAddress,
      LocalDate dueDate,
      @Size(max = 1000, message = "As observações não podem exceder 1000 caracteres.") String notes,
      @NotEmpty(message = "Acrescente pelo menos uma linha.")
          @Size(max = 200, message = "Máximo de 200 linhas.")
          List<@Valid LineRequest> lines,
      Long version) {}

  /** Na emissão de uma factura-recibo, o pagamento é obrigatório. */
  public record IssueRequest(
      PaymentMethod paymentMethod,
      @Size(max = 100, message = "A referência não pode exceder 100 caracteres.")
          String reference) {}

  public record PaymentRequest(
      @NotNull(message = "Indique o valor.")
          @DecimalMin(value = "0.01", message = "O valor tem de ser positivo.")
          @Digits(integer = 12, fraction = 2, message = "Valor inválido (máx. 2 casas decimais).")
          BigDecimal amount,
      @NotNull(message = "Seleccione o meio de pagamento.") PaymentMethod method,
      @Size(max = 100, message = "A referência não pode exceder 100 caracteres.")
          String reference) {}

  public record ReasonRequest(
      @NotBlank(message = "Indique o motivo.")
          @Size(max = 500, message = "O motivo não pode exceder 500 caracteres.")
          String reason) {}

  public record ItemResponse(
      int lineNumber,
      UUID servicePriceId,
      String code,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountPercent,
      BigDecimal vatRate,
      String vatExemptionReason,
      BigDecimal discountAmount,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount) {}

  public record PaymentResponse(
      UUID id,
      String receiptNumber,
      BigDecimal amount,
      PaymentMethod method,
      String reference,
      String receivedByName,
      Instant receivedAt) {}

  public record InvoiceSummary(
      UUID id,
      DocumentType documentType,
      String number,
      InvoiceStatus status,
      UUID patientId,
      String patientName,
      String patientRecordNumber,
      String customerName,
      BigDecimal grossTotal,
      BigDecimal paidTotal,
      BigDecimal balance,
      Instant createdAt,
      Instant issuedAt,
      LocalDate dueDate,
      boolean overdue) {}

  public record InvoiceResponse(
      UUID id,
      DocumentType documentType,
      String number,
      InvoiceStatus status,
      UUID patientId,
      String patientName,
      String patientRecordNumber,
      UUID episodeId,
      String customerName,
      String customerTaxId,
      String customerAddress,
      String issuerName,
      String issuerTaxId,
      String issuerAddress,
      String currency,
      BigDecimal netTotal,
      BigDecimal vatTotal,
      BigDecimal grossTotal,
      BigDecimal paidTotal,
      BigDecimal balance,
      LocalDate dueDate,
      boolean overdue,
      String notes,
      UUID relatedInvoiceId,
      String relatedInvoiceNumber,
      String creditReason,
      UUID creditNoteId,
      String creditNoteNumber,
      String createdByName,
      Instant createdAt,
      String issuedByName,
      Instant issuedAt,
      String cancelledByName,
      Instant cancelledAt,
      String cancellationReason,
      List<ItemResponse> items,
      List<PaymentResponse> payments,
      long version) {}

  /** Caixa do dia: recebimentos por meio de pagamento. */
  public record CashSummary(
      LocalDate date,
      BigDecimal total,
      Map<PaymentMethod, BigDecimal> byMethod,
      int receipts,
      List<PaymentResponse> payments) {}
}
