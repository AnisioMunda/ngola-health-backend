package com.ngolahealth.backend.modules.pharmacy.application;

import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.MedicationRequest;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.MedicationResponse;
import com.ngolahealth.backend.modules.pharmacy.domain.Medication;
import com.ngolahealth.backend.modules.pharmacy.domain.MedicationDetails;
import com.ngolahealth.backend.modules.pharmacy.domain.MedicationRepository;
import com.ngolahealth.backend.modules.pharmacy.domain.StockBatchRepository;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo de medicamentos do hospital, com o stock utilizável de cada um. */
@Service
@RequiredArgsConstructor
public class MedicationService {

  static final String NOT_FOUND = "MEDICATION_NOT_FOUND";

  private final MedicationRepository medications;
  private final StockBatchRepository batches;
  private final CurrentUserProvider currentUser;
  private final AuditTrail audit;
  private final AppProperties app;
  private final Clock clock;

  @Transactional(readOnly = true)
  public PageResponse<MedicationResponse> search(
      String search, Boolean active, int page, int size) {
    requireHospitalUser();
    var result =
        medications.search(
            likePattern(search),
            active,
            PageRequest.of(page, size, Sort.by("name").and(Sort.by("strength"))));
    var stock = stockInfo(result.getContent().stream().map(Medication::getId).toList());
    return PageResponse.of(result, m -> toResponse(m, stock));
  }

  @Transactional(readOnly = true)
  public MedicationResponse findById(UUID id) {
    requireHospitalUser();
    var m = load(id);
    return toResponse(m, stockInfo(List.of(id)));
  }

  @Transactional
  public MedicationResponse create(MedicationRequest request) {
    requireHospitalUser();
    var details = normalize(request);
    ensureUnique(details, null);
    var m = medications.saveAndFlush(new Medication(details));
    var response = toResponse(m, stockInfo(List.of(m.getId())));
    audit.record(
        entry(AuditAction.CREATE, m, "Medicamento adicionado ao catálogo: " + label(m))
            .withChanges(audit.snapshot(response)));
    return response;
  }

  @Transactional
  public MedicationResponse update(UUID id, MedicationRequest request) {
    requireHospitalUser();
    var m = load(id);
    if (request.version() != null && request.version() != m.getVersion()) {
      throw new ConflictException(
          "CONCURRENT_MODIFICATION",
          "O medicamento foi alterado por outra pessoa. Actualize e tente novamente.");
    }
    var details = normalize(request);
    ensureUnique(details, id);
    var before = toResponse(m, stockInfo(List.of(id)));
    m.apply(details);
    var after = toResponse(medications.saveAndFlush(m), stockInfo(List.of(id)));
    audit.record(
        entry(AuditAction.UPDATE, m, "Medicamento editado: " + label(m))
            .withChanges(audit.diff(before, after)));
    return after;
  }

  @Transactional
  public MedicationResponse setActive(UUID id, boolean active) {
    requireHospitalUser();
    var m = load(id);
    if (m.isActive() != active) {
      if (active) {
        m.activate();
      } else {
        m.deactivate();
      }
      m = medications.saveAndFlush(m);
      audit.record(
          entry(
              active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE,
              m,
              "Medicamento " + (active ? "reactivado: " : "desactivado: ") + label(m)));
    } else {
      audit.markHandled();
    }
    return toResponse(m, stockInfo(List.of(id)));
  }

  Medication load(UUID id) {
    return medications
        .findScoped(id)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND, "Medicamento não encontrado."));
  }

  record StockInfo(Map<UUID, Integer> totals, Map<UUID, LocalDate> nextExpiry) {}

  StockInfo stockInfo(List<UUID> ids) {
    Map<UUID, Integer> totals = new HashMap<>();
    Map<UUID, LocalDate> expiry = new HashMap<>();
    if (!ids.isEmpty()) {
      var today = today();
      for (Object[] row : batches.usableTotals(ids, today)) {
        totals.put((UUID) row[0], ((Number) row[1]).intValue());
      }
      for (Object[] row : batches.nextExpiry(ids, today)) {
        expiry.put((UUID) row[0], (LocalDate) row[1]);
      }
    }
    return new StockInfo(totals, expiry);
  }

  MedicationResponse toResponse(Medication m, StockInfo stock) {
    int available = stock.totals().getOrDefault(m.getId(), 0);
    return new MedicationResponse(
        m.getId(),
        m.getName(),
        m.getGenericName(),
        m.getDosageForm(),
        m.getStrength(),
        m.getDispensingUnit(),
        m.isRequiresPrescription(),
        m.getMinStockLevel(),
        m.isActive(),
        available,
        m.isActive() && available <= m.getMinStockLevel(),
        stock.nextExpiry().get(m.getId()),
        m.getVersion());
  }

  static String label(Medication m) {
    return m.getName() + (m.getStrength() == null ? "" : " " + m.getStrength());
  }

  LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), app.timeZone());
  }

  void requireHospitalUser() {
    if (currentUser.get().hospitalId() == null) {
      throw new ForbiddenException(
          "HOSPITAL_SCOPE_REQUIRED", "A farmácia só está acessível a utilizadores de um hospital.");
    }
  }

  private AuditEntry entry(AuditAction action, Medication m, String summary) {
    return AuditEntry.success(
        action, AuditEntityType.MEDICATION, m.getId(), m.getHospitalId(), summary);
  }

  private void ensureUnique(MedicationDetails d, UUID ignoreId) {
    if (medications.existsSame(d.name(), d.dosageForm(), d.strength(), ignoreId)) {
      throw new ConflictException(
          "MEDICATION_DUPLICATE",
          "Já existe no catálogo: "
              + d.name()
              + (d.strength() == null ? "" : " " + d.strength())
              + ".");
    }
  }

  static MedicationDetails normalize(MedicationRequest r) {
    return new MedicationDetails(
        r.name().strip().replaceAll("\\s+", " "),
        optional(r.genericName()),
        r.dosageForm(),
        optional(r.strength()),
        r.dispensingUnit().strip(),
        r.requiresPrescription(),
        r.minStockLevel());
  }

  private static String likePattern(String search) {
    var term = optional(search);
    return term == null
        ? null
        : "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
