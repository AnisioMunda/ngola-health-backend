package com.ngolahealth.backend.modules.financial.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.ServicePriceRequest;
import com.ngolahealth.backend.modules.financial.api.FinancialDtos.ServicePriceResponse;
import com.ngolahealth.backend.modules.financial.domain.ServiceCategory;
import com.ngolahealth.backend.modules.financial.domain.ServicePrice;
import com.ngolahealth.backend.modules.financial.domain.ServicePriceDetails;
import com.ngolahealth.backend.modules.financial.domain.ServicePriceRepository;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tabela de preços do hospital. */
@Service
@RequiredArgsConstructor
public class PriceService {

  private final ServicePriceRepository prices;
  private final CurrentUserProvider currentUser;
  private final AuditTrail audit;

  @Transactional(readOnly = true)
  public List<ServicePriceResponse> search(
      String search, ServiceCategory category, Boolean active) {
    hospitalUser();
    var term =
        search == null || search.isBlank()
            ? null
            : "%" + escape(search.strip().toLowerCase()) + "%";
    return prices.search(term, category, active).stream().map(PriceService::toResponse).toList();
  }

  @Transactional
  public ServicePriceResponse create(ServicePriceRequest r) {
    var user = hospitalUser();
    var d = normalize(r);
    if (prices.codeTaken(d.code(), null)) {
      throw codeInUse(d.code());
    }
    var p = prices.saveAndFlush(new ServicePrice(d));
    var response = toResponse(p);
    audit.record(
        AuditEntry.success(
                AuditAction.CREATE,
                AuditEntityType.SERVICE_PRICE,
                p.getId(),
                user.hospitalId(),
                "Preço criado: "
                    + p.getCode()
                    + " — "
                    + p.getDescription()
                    + " "
                    + p.getUnitPrice()
                    + " AOA")
            .withChanges(audit.snapshot(response)));
    return response;
  }

  @Transactional
  public ServicePriceResponse update(UUID id, ServicePriceRequest r) {
    var user = hospitalUser();
    var p = load(id);
    if (r.version() != null && !r.version().equals(p.getVersion())) {
      throw new ConflictException(
          "CONCURRENT_MODIFICATION", "O preço foi alterado por outra pessoa. Actualize.");
    }
    var d = normalize(r);
    if (prices.codeTaken(d.code(), id)) {
      throw codeInUse(d.code());
    }
    var before = toResponse(p);
    p.apply(d);
    var after = toResponse(prices.saveAndFlush(p));
    audit.record(
        AuditEntry.success(
                AuditAction.UPDATE,
                AuditEntityType.SERVICE_PRICE,
                id,
                user.hospitalId(),
                "Preço editado: " + p.getCode() + " (não altera documentos já feitos)")
            .withChanges(audit.diff(before, after)));
    return after;
  }

  @Transactional
  public ServicePriceResponse setActive(UUID id, boolean active) {
    var user = hospitalUser();
    var p = load(id);
    p.setActive(active);
    var response = toResponse(prices.saveAndFlush(p));
    audit.record(
        AuditEntry.success(
            active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE,
            AuditEntityType.SERVICE_PRICE,
            id,
            user.hospitalId(),
            (active ? "Preço activado: " : "Preço desactivado: ") + p.getCode()));
    return response;
  }

  CurrentUser hospitalUser() {
    var user = currentUser.get();
    if (user.hospitalId() == null) {
      throw new ForbiddenException(
          "HOSPITAL_SCOPE_REQUIRED",
          "O financeiro só está acessível a utilizadores de um hospital.");
    }
    return user;
  }

  private ServicePrice load(UUID id) {
    return prices
        .findScoped(id)
        .orElseThrow(() -> new NotFoundException("PRICE_NOT_FOUND", "Preço não encontrado."));
  }

  static ServicePriceDetails normalize(ServicePriceRequest r) {
    var reason = blankToNull(r.vatExemptionReason());
    if (r.vatRate().signum() == 0 && reason == null) {
      throw new BadRequestException(
          "VAT_EXEMPTION_REQUIRED", "Com IVA a 0%, indique o motivo de isenção.");
    }
    return new ServicePriceDetails(
        r.code().strip().toUpperCase(),
        r.description().strip(),
        r.category(),
        r.unitPrice(),
        r.vatRate(),
        r.vatRate().signum() == 0 ? reason : null);
  }

  static ServicePriceResponse toResponse(ServicePrice p) {
    return new ServicePriceResponse(
        p.getId(),
        p.getCode(),
        p.getDescription(),
        p.getCategory(),
        p.getUnitPrice(),
        p.getVatRate(),
        p.getVatExemptionReason(),
        p.isActive(),
        p.getVersion() == null ? 0 : p.getVersion());
  }

  private static ConflictException codeInUse(String code) {
    return new ConflictException(
        "PRICE_CODE_IN_USE", "Já existe um preço com o código " + code + ".");
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }
}
