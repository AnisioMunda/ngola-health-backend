package com.ngolahealth.backend.modules.financial.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.episodes.application.EpisodeDirectory;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.CashSummary;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.InvoiceRequest;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.InvoiceResponse;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.InvoiceSummary;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.IssueRequest;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.ItemResponse;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.LineRequest;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.PaymentRequest;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.PaymentResponse;
import com.ngolahealth.backend.modules.financial.domain.Customer;
import com.ngolahealth.backend.modules.financial.domain.DocumentNumbers;
import com.ngolahealth.backend.modules.financial.domain.DocumentType;
import com.ngolahealth.backend.modules.financial.domain.Invoice;
import com.ngolahealth.backend.modules.financial.domain.InvoiceRepository;
import com.ngolahealth.backend.modules.financial.domain.InvoiceStatus;
import com.ngolahealth.backend.modules.financial.domain.Issuer;
import com.ngolahealth.backend.modules.financial.domain.LineInput;
import com.ngolahealth.backend.modules.financial.domain.Money;
import com.ngolahealth.backend.modules.financial.domain.Payment;
import com.ngolahealth.backend.modules.financial.domain.PaymentMethod;
import com.ngolahealth.backend.modules.financial.domain.PaymentRepository;
import com.ngolahealth.backend.modules.financial.domain.ServicePrice;
import com.ngolahealth.backend.modules.financial.domain.ServicePriceRepository;
import com.ngolahealth.backend.modules.hospitals.application.HospitalService;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documentos de facturação: rascunho, emissão (número sem falhas), pagamentos com recibo, anulação
 * e nota de crédito. Valores sempre calculados no servidor.
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

  private final InvoiceRepository invoices;
  private final PaymentRepository payments;
  private final ServicePriceRepository prices;
  private final DocumentNumbers numbers;
  private final PriceService priceService;
  private final PatientDirectory patients;
  private final EpisodeDirectory episodes;
  private final HospitalService hospitals;
  private final StaffDirectory staff;
  private final AuditTrail audit;
  private final AppProperties app;
  private final Clock clock;

  // ---------- Consulta ----------

  @Transactional(readOnly = true)
  public PageResponse<InvoiceSummary> search(
      InvoiceStatus status,
      DocumentType type,
      UUID patientId,
      UUID episodeId,
      String number,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    priceService.hospitalUser();
    var result =
        invoices.search(
            status == null ? EnumSet.allOf(InvoiceStatus.class) : EnumSet.of(status),
            type == null ? EnumSet.allOf(DocumentType.class) : EnumSet.of(type),
            patientId,
            episodeId,
            number == null || number.isBlank() ? null : "%" + number.strip().toUpperCase() + "%",
            from == null ? null : from.atStartOfDay(app.timeZone()).toInstant(),
            to == null ? null : to.plusDays(1).atStartOfDay(app.timeZone()).toInstant(),
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
    var refs = patients.findAll(result.getContent().stream().map(Invoice::getPatientId).toList());
    var today = today();
    return PageResponse.of(
        result,
        i -> {
          var p = refs.get(i.getPatientId());
          return new InvoiceSummary(
              i.getId(),
              i.getDocumentType(),
              i.getNumber(),
              i.getStatus(),
              i.getPatientId(),
              p == null ? null : p.fullName(),
              p == null ? null : p.recordNumber(),
              i.getCustomerName(),
              i.getGrossTotal(),
              i.getPaidTotal(),
              i.balance(),
              i.getCreatedAt(),
              i.getIssuedAt(),
              i.getDueDate(),
              overdue(i, today));
        });
  }

  @Transactional(readOnly = true)
  public InvoiceResponse view(UUID id) {
    priceService.hospitalUser();
    return toResponse(load(id));
  }

  // ---------- Rascunho ----------

  @Transactional
  public InvoiceResponse create(InvoiceRequest r) {
    var user = priceService.hospitalUser();
    var patient =
        patients
            .find(r.patientId())
            .orElseThrow(
                () -> new BadRequestException("PATIENT_NOT_FOUND", "O paciente não existe."));
    validateEpisode(r.episodeId(), r.patientId());
    validateDueDate(r.dueDate());
    var invoice =
        invoices.saveAndFlush(
            new Invoice(
                r.documentType(),
                patient.id(),
                r.episodeId(),
                customer(r, patient.fullName()),
                r.dueDate(),
                PriceService.blankToNull(r.notes()),
                lines(r.lines()),
                user.id()));
    var response = toResponse(invoice);
    audit.record(entry(AuditAction.CREATE, invoice, "Rascunho criado: " + describe(response)));
    return response;
  }

  @Transactional
  public InvoiceResponse update(UUID id, InvoiceRequest r) {
    priceService.hospitalUser();
    var invoice = lock(id);
    if (r.version() != null && !r.version().equals(invoice.getVersion())) {
      throw new ConflictException(
          "CONCURRENT_MODIFICATION", "O documento foi alterado por outra pessoa. Actualize.");
    }
    if (r.documentType() != invoice.getDocumentType()
        || !r.patientId().equals(invoice.getPatientId())) {
      throw new BadRequestException(
          "IMMUTABLE_FIELDS", "O tipo de documento e o paciente não mudam: crie outro rascunho.");
    }
    var patient = patients.find(invoice.getPatientId()).orElseThrow();
    validateEpisode(r.episodeId(), r.patientId());
    validateDueDate(r.dueDate());
    invoice.edit(
        r.episodeId(),
        customer(r, patient.fullName()),
        r.dueDate(),
        PriceService.blankToNull(r.notes()),
        lines(r.lines()));
    var response = toResponse(invoices.saveAndFlush(invoice));
    audit.record(entry(AuditAction.UPDATE, invoice, "Rascunho editado: " + describe(response)));
    return response;
  }

  /** Um rascunho não é documento fiscal: pode ser eliminado. */
  @Transactional
  public void deleteDraft(UUID id) {
    priceService.hospitalUser();
    var invoice = lock(id);
    invoice.requireDraft();
    var response = toResponse(invoice);
    invoices.delete(invoice);
    audit.record(entry(AuditAction.DELETE, invoice, "Rascunho eliminado: " + describe(response)));
  }

  // ---------- Emissão e pagamentos ----------

  @Transactional
  public InvoiceResponse issue(UUID id, IssueRequest r) {
    var user = priceService.hospitalUser();
    var invoice = lock(id);
    invoice.requireDraft();
    boolean receipt = invoice.getDocumentType() == DocumentType.FR;
    if (receipt && (r == null || r.paymentMethod() == null)) {
      throw new BadRequestException(
          "PAYMENT_METHOD_REQUIRED",
          "A factura-recibo é paga na emissão: indique o meio de pagamento.");
    }
    var assigned =
        numbers.next(user.hospitalId(), invoice.getDocumentType().name(), today().getYear());
    var now = clock.instant();
    invoice.issue(
        assigned.year(), assigned.sequence(), assigned.number(), issuer(user), user.id(), now);
    if (receipt) {
      var total = invoice.getGrossTotal();
      invoice.registerPayment(total);
      payments.save(
          new Payment(
              invoice.getId(),
              assigned.number(),
              total,
              r.paymentMethod(),
              PriceService.blankToNull(r.reference()),
              user.id(),
              now));
    }
    var response = toResponse(invoices.saveAndFlush(invoice));
    audit.record(
        entry(AuditAction.ISSUE, invoice, "Documento emitido: " + describe(response))
            .withChanges(audit.snapshot(response)));
    return response;
  }

  @Transactional
  public InvoiceResponse pay(UUID id, PaymentRequest r) {
    var user = priceService.hospitalUser();
    var invoice = lock(id);
    invoice.registerPayment(r.amount());
    var receipt = numbers.next(user.hospitalId(), "RC", today().getYear());
    payments.save(
        new Payment(
            invoice.getId(),
            receipt.number(),
            r.amount(),
            r.method(),
            PriceService.blankToNull(r.reference()),
            user.id(),
            clock.instant()));
    var response = toResponse(invoices.saveAndFlush(invoice));
    audit.record(
        entry(
            AuditAction.PAYMENT,
            invoice,
            "Pagamento "
                + receipt.number()
                + " de "
                + Money.round(r.amount())
                + " AOA ("
                + r.method()
                + "): "
                + describe(response)));
    return response;
  }

  @Transactional
  public InvoiceResponse cancel(UUID id, String reason) {
    var user = priceService.hospitalUser();
    var invoice = lock(id);
    invoice.cancel(reason.strip(), user.id(), clock.instant());
    var response = toResponse(invoices.saveAndFlush(invoice));
    audit.record(
        entry(
            AuditAction.CANCEL,
            invoice,
            "Documento anulado (" + reason.strip() + "): " + describe(response)));
    return response;
  }

  /** Nota de crédito pelo valor total do documento, emitida de imediato. */
  @Transactional
  public InvoiceResponse creditNote(UUID id, String reason) {
    var user = priceService.hospitalUser();
    var original = lock(id);
    var nc = Invoice.creditNoteFor(original, reason.strip(), user.id());
    var assigned = numbers.next(user.hospitalId(), "NC", today().getYear());
    nc.issue(
        assigned.year(),
        assigned.sequence(),
        assigned.number(),
        issuer(user),
        user.id(),
        clock.instant());
    original.markCredited();
    invoices.saveAndFlush(original);
    var saved = invoices.saveAndFlush(nc);
    var response = toResponse(saved);
    audit.record(
        entry(
            AuditAction.CREDIT_NOTE,
            original,
            "Nota de crédito "
                + assigned.number()
                + " sobre "
                + original.getNumber()
                + " ("
                + reason.strip()
                + ")"));
    audit.record(entry(AuditAction.ISSUE, saved, "Documento emitido: " + describe(response)));
    return response;
  }

  // ---------- Caixa ----------

  @Transactional(readOnly = true)
  public CashSummary cash(LocalDate date) {
    priceService.hospitalUser();
    var day = date != null ? date : today();
    var list =
        payments.between(
            day.atStartOfDay(app.timeZone()).toInstant(),
            day.plusDays(1).atStartOfDay(app.timeZone()).toInstant());
    var byMethod = new EnumMap<PaymentMethod, BigDecimal>(PaymentMethod.class);
    list.forEach(p -> byMethod.merge(p.getMethod(), p.getAmount(), BigDecimal::add));
    var total = list.stream().map(Payment::getAmount).reduce(Money.zero(), BigDecimal::add);
    return new CashSummary(day, total, byMethod, list.size(), toPayments(list));
  }

  // ---------- Auxiliares ----------

  private List<LineInput> lines(List<LineRequest> requests) {
    var ids =
        requests.stream()
            .map(LineRequest::servicePriceId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    var catalog =
        ids.isEmpty()
            ? java.util.Map.<UUID, ServicePrice>of()
            : prices.findAllScoped(ids).stream()
                .collect(Collectors.toMap(ServicePrice::getId, Function.identity()));
    var result = new ArrayList<LineInput>();
    for (var l : requests) {
      var discount = l.discountPercent() == null ? BigDecimal.ZERO : l.discountPercent();
      if (l.servicePriceId() != null) {
        var p = catalog.get(l.servicePriceId());
        if (p == null) {
          throw new BadRequestException(
              "PRICE_NOT_FOUND", "Um dos actos não existe na tabela de preços.");
        }
        if (!p.isActive()) {
          throw new ConflictException(
              "PRICE_INACTIVE", "O acto " + p.getDescription() + " está desactivado.");
        }
        // O preço e o IVA vêm sempre da tabela: a recepção não os altera.
        result.add(
            new LineInput(
                p.getId(),
                p.getCode(),
                p.getDescription(),
                l.quantity(),
                p.getUnitPrice(),
                discount,
                p.getVatRate(),
                p.getVatExemptionReason()));
      } else {
        if (l.description() == null
            || l.description().isBlank()
            || l.unitPrice() == null
            || l.vatRate() == null) {
          throw new BadRequestException(
              "INVALID_LINE", "Uma linha livre precisa de descrição, preço e taxa de IVA.");
        }
        result.add(
            new LineInput(
                null,
                null,
                l.description().strip(),
                l.quantity(),
                l.unitPrice(),
                discount,
                l.vatRate(),
                l.vatRate().signum() == 0
                    ? PriceService.blankToNull(l.vatExemptionReason())
                    : null));
      }
    }
    return result;
  }

  private static Customer customer(InvoiceRequest r, String patientName) {
    var name = PriceService.blankToNull(r.customerName());
    var taxId = PriceService.blankToNull(r.customerTaxId());
    return new Customer(
        name != null ? name : patientName,
        taxId == null ? null : taxId.toUpperCase(),
        PriceService.blankToNull(r.customerAddress()));
  }

  private Issuer issuer(CurrentUser user) {
    var profile = hospitals.fiscalProfile(user.hospitalId()).orElseThrow();
    if (profile.taxId() == null || profile.taxId().isBlank()) {
      throw new ConflictException(
          "HOSPITAL_TAX_ID_MISSING",
          "O hospital não tem NIF registado: peça ao administrador da plataforma.");
    }
    return new Issuer(profile.name(), profile.taxId(), profile.address());
  }

  private void validateEpisode(UUID episodeId, UUID patientId) {
    if (episodeId == null) {
      return;
    }
    var episode =
        episodes
            .find(episodeId)
            .orElseThrow(
                () -> new BadRequestException("EPISODE_NOT_FOUND", "O episódio não existe."));
    if (!episode.patientId().equals(patientId)) {
      throw new BadRequestException("EPISODE_PATIENT_MISMATCH", "O episódio é de outro paciente.");
    }
  }

  private void validateDueDate(LocalDate dueDate) {
    if (dueDate != null && dueDate.isBefore(today())) {
      throw new BadRequestException("INVALID_DUE_DATE", "O vencimento não pode ser no passado.");
    }
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), app.timeZone());
  }

  private static boolean overdue(Invoice i, LocalDate today) {
    return i.getDueDate() != null
        && i.getDueDate().isBefore(today)
        && (i.getStatus() == InvoiceStatus.ISSUED || i.getStatus() == InvoiceStatus.PARTIALLY_PAID);
  }

  private Invoice load(UUID id) {
    return invoices
        .findScoped(id)
        .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND", "Documento não encontrado."));
  }

  private Invoice lock(UUID id) {
    return invoices
        .findForUpdate(id)
        .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND", "Documento não encontrado."));
  }

  private List<PaymentResponse> toPayments(List<Payment> list) {
    var names = staff.names(list.stream().map(Payment::getReceivedBy).toList());
    return list.stream()
        .map(
            p ->
                new PaymentResponse(
                    p.getId(),
                    p.getReceiptNumber(),
                    p.getAmount(),
                    p.getMethod(),
                    p.getReference(),
                    names.get(p.getReceivedBy()),
                    p.getReceivedAt()))
        .toList();
  }

  private InvoiceResponse toResponse(Invoice i) {
    var p = patients.find(i.getPatientId()).orElse(null);
    var people = new HashSet<UUID>();
    Stream.of(i.getCreatedBy(), i.getIssuedBy(), i.getCancelledBy())
        .filter(Objects::nonNull)
        .forEach(people::add);
    var names = staff.names(people);
    var related =
        i.getRelatedInvoiceId() == null
            ? null
            : invoices.findScoped(i.getRelatedInvoiceId()).orElse(null);
    var creditNote =
        i.getDocumentType() == DocumentType.NC
            ? null
            : invoices.findCreditNoteOf(i.getId()).orElse(null);
    return new InvoiceResponse(
        i.getId(),
        i.getDocumentType(),
        i.getNumber(),
        i.getStatus(),
        i.getPatientId(),
        p == null ? null : p.fullName(),
        p == null ? null : p.recordNumber(),
        i.getEpisodeId(),
        i.getCustomerName(),
        i.getCustomerTaxId(),
        i.getCustomerAddress(),
        i.getIssuerName(),
        i.getIssuerTaxId(),
        i.getIssuerAddress(),
        i.getCurrency(),
        i.getNetTotal(),
        i.getVatTotal(),
        i.getGrossTotal(),
        i.getPaidTotal(),
        i.balance(),
        i.getDueDate(),
        overdue(i, today()),
        i.getNotes(),
        i.getRelatedInvoiceId(),
        related == null ? null : related.getNumber(),
        i.getCreditReason(),
        creditNote == null ? null : creditNote.getId(),
        creditNote == null ? null : creditNote.getNumber(),
        names.get(i.getCreatedBy()),
        i.getCreatedAt(),
        names.get(i.getIssuedBy()),
        i.getIssuedAt(),
        names.get(i.getCancelledBy()),
        i.getCancelledAt(),
        i.getCancellationReason(),
        i.getItems().stream()
            .map(
                it ->
                    new ItemResponse(
                        it.getLineNumber(),
                        it.getServicePriceId(),
                        it.getCode(),
                        it.getDescription(),
                        it.getQuantity(),
                        it.getUnitPrice(),
                        it.getDiscountPercent(),
                        it.getVatRate(),
                        it.getVatExemptionReason(),
                        it.getDiscountAmount(),
                        it.getNetAmount(),
                        it.getVatAmount(),
                        it.getGrossAmount()))
            .toList(),
        toPayments(payments.forInvoice(i.getId())),
        i.getVersion() == null ? 0 : i.getVersion());
  }

  private static String describe(InvoiceResponse r) {
    return (r.number() != null ? r.number() : r.documentType() + " (rascunho)")
        + " — "
        + r.customerName()
        + " — "
        + r.grossTotal()
        + " AOA";
  }

  private static AuditEntry entry(AuditAction action, Invoice i, String summary) {
    return AuditEntry.success(
        action, AuditEntityType.INVOICE, i.getId(), i.getHospitalId(), summary);
  }
}
