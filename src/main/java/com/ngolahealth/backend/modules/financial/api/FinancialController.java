package com.ngolahealth.backend.modules.financial.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.CashSummary;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.InvoiceRequest;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.InvoiceResponse;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.InvoiceSummary;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.IssueRequest;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.PaymentRequest;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.ReasonRequest;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.ServicePriceRequest;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.ServicePriceResponse;
import com.ngolahealth.backend.modules.financial.application.InvoiceService;
import com.ngolahealth.backend.modules.financial.application.PriceService;
import com.ngolahealth.backend.modules.financial.domain.DocumentType;
import com.ngolahealth.backend.modules.financial.domain.InvoiceStatus;
import com.ngolahealth.backend.modules.financial.domain.ServiceCategory;
import com.ngolahealth.backend.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/financial")
@RequiredArgsConstructor
@Tag(name = "Financeiro", description = "Tabela de preços, facturas, pagamentos e caixa")
public class FinancialController {

  private final PriceService prices;
  private final InvoiceService invoices;

  // ---------- Preços ----------

  @GetMapping("/prices")
  @PreAuthorize(Authorities.FINANCE_READERS)
  public List<ServicePriceResponse> prices(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) ServiceCategory category,
      @RequestParam(required = false) Boolean active) {
    return prices.search(search, category, active);
  }

  @PostMapping("/prices")
  @PreAuthorize(Authorities.PRICE_MANAGERS)
  @ResponseStatus(HttpStatus.CREATED)
  public ServicePriceResponse createPrice(@Valid @RequestBody ServicePriceRequest request) {
    return prices.create(request);
  }

  @PutMapping("/prices/{id}")
  @PreAuthorize(Authorities.PRICE_MANAGERS)
  @Operation(summary = "Editar preço (não altera documentos já feitos)")
  public ServicePriceResponse updatePrice(
      @PathVariable UUID id, @Valid @RequestBody ServicePriceRequest request) {
    return prices.update(id, request);
  }

  @PatchMapping("/prices/{id}/activate")
  @PreAuthorize(Authorities.PRICE_MANAGERS)
  public ServicePriceResponse activatePrice(@PathVariable UUID id) {
    return prices.setActive(id, true);
  }

  @PatchMapping("/prices/{id}/deactivate")
  @PreAuthorize(Authorities.PRICE_MANAGERS)
  public ServicePriceResponse deactivatePrice(@PathVariable UUID id) {
    return prices.setActive(id, false);
  }

  // ---------- Documentos ----------

  @GetMapping("/invoices")
  @PreAuthorize(Authorities.FINANCE_READERS)
  public PageResponse<InvoiceSummary> invoices(
      @RequestParam(required = false) InvoiceStatus status,
      @RequestParam(required = false) DocumentType type,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) UUID episodeId,
      @RequestParam(required = false) String number,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 100, message = "O tamanho da página não pode exceder 100.")
          int size) {
    return invoices.search(status, type, patientId, episodeId, number, from, to, page, size);
  }

  @GetMapping("/invoices/{id}")
  @PreAuthorize(Authorities.FINANCE_READERS)
  public InvoiceResponse invoice(@PathVariable UUID id) {
    return invoices.view(id);
  }

  @PostMapping("/invoices")
  @PreAuthorize(Authorities.BILLING_CLERKS)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Criar rascunho (sem número; valores calculados no servidor)")
  public InvoiceResponse create(@Valid @RequestBody InvoiceRequest request) {
    return invoices.create(request);
  }

  @PutMapping("/invoices/{id}")
  @PreAuthorize(Authorities.BILLING_CLERKS)
  public InvoiceResponse update(@PathVariable UUID id, @Valid @RequestBody InvoiceRequest request) {
    return invoices.update(id, request);
  }

  @DeleteMapping("/invoices/{id}")
  @PreAuthorize(Authorities.BILLING_CLERKS)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Eliminar rascunho (documentos emitidos nunca se eliminam)")
  public void delete(@PathVariable UUID id) {
    invoices.deleteDraft(id);
  }

  @PostMapping("/invoices/{id}/issue")
  @PreAuthorize(Authorities.BILLING_CLERKS)
  @Operation(summary = "Emitir (atribui o número; FR exige o meio de pagamento)")
  public InvoiceResponse issue(
      @PathVariable UUID id, @Valid @RequestBody(required = false) IssueRequest request) {
    return invoices.issue(id, request);
  }

  @PostMapping("/invoices/{id}/payments")
  @PreAuthorize(Authorities.BILLING_CLERKS)
  @Operation(summary = "Registar pagamento (gera recibo RC)")
  public InvoiceResponse pay(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
    return invoices.pay(id, request);
  }

  @PostMapping("/invoices/{id}/cancel")
  @PreAuthorize(Authorities.FINANCE_SUPERVISORS)
  @Operation(summary = "Anular documento emitido sem pagamentos")
  public InvoiceResponse cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
    return invoices.cancel(id, request.reason());
  }

  @PostMapping("/invoices/{id}/credit-note")
  @PreAuthorize(Authorities.FINANCE_SUPERVISORS)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Emitir nota de crédito pelo valor total")
  public InvoiceResponse creditNote(
      @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
    return invoices.creditNote(id, request.reason());
  }

  // ---------- Caixa ----------

  @GetMapping("/cash")
  @PreAuthorize(Authorities.FINANCE_READERS)
  @Operation(summary = "Recebimentos do dia por meio de pagamento")
  public CashSummary cash(@RequestParam(required = false) LocalDate date) {
    return invoices.cash(date);
  }
}
