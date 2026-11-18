package com.ngolahealth.backend.modules.equipment.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentOverview;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentRequest;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentResponse;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentSummary;
import com.ngolahealth.backend.modules.equipment.domain.Criticality;
import com.ngolahealth.backend.modules.equipment.domain.Equipment;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentCategory;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentDetails;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentFaultRepository;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentRepository;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentStatus;
import com.ngolahealth.backend.modules.inpatient.application.WardDirectory;
import com.ngolahealth.backend.security.CurrentUser;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inventário de equipamentos: registo, edição, envio para manutenção, abate e consultas. */
@Service
@RequiredArgsConstructor
public class EquipmentService {

  /** Ordem de urgência: os críticos primeiro. */
  static final Comparator<Equipment> BY_CRITICALITY =
      Comparator.comparing(
              (Equipment e) ->
                  e.getCriticality() == Criticality.HIGH
                      ? 0
                      : e.getCriticality() == Criticality.MEDIUM ? 1 : 2)
          .thenComparing(Equipment::getName);

  private final EquipmentRepository equipment;
  private final EquipmentFaultRepository faults;
  private final WardDirectory wards;
  private final EquipmentSupport support;
  private final AuditTrail audit;

  @Transactional(readOnly = true)
  public PageResponse<EquipmentSummary> search(
      EquipmentStatus status,
      EquipmentCategory category,
      UUID wardId,
      String search,
      boolean includeRetired,
      int page,
      int size) {
    support.hospitalUser();
    var statuses =
        status != null
            ? EnumSet.of(status)
            : includeRetired
                ? EnumSet.allOf(EquipmentStatus.class)
                : EnumSet.complementOf(EnumSet.of(EquipmentStatus.RETIRED));
    var term =
        search == null || search.isBlank()
            ? null
            : "%" + escape(search.strip().toLowerCase()) + "%";
    var result =
        equipment.search(
            statuses,
            category,
            wardId,
            term,
            PageRequest.of(page, size, Sort.by("name").and(Sort.by("id"))));
    var mapped = support.toSummaries(result.getContent());
    var byId = new java.util.HashMap<UUID, EquipmentSummary>();
    mapped.forEach(m -> byId.put(m.id(), m));
    return PageResponse.of(result, e -> byId.get(e.getId()));
  }

  @Transactional(readOnly = true)
  public EquipmentResponse view(UUID id) {
    support.hospitalUser();
    return support.toResponse(support.load(id));
  }

  /** Vencidos ou a vencer nos próximos {@code days} dias, críticos primeiro. */
  @Transactional(readOnly = true)
  public List<EquipmentSummary> due(int days) {
    support.hospitalUser();
    if (days < 0 || days > 365) {
      throw new BadRequestException("INVALID_DAYS", "O prazo deve estar entre 0 e 365 dias.");
    }
    return support.toSummaries(
        equipment.dueBy(support.today().plusDays(days)).stream().sorted(BY_CRITICALITY).toList());
  }

  @Transactional(readOnly = true)
  public EquipmentOverview overview() {
    support.hospitalUser();
    var counts = new java.util.EnumMap<EquipmentStatus, Long>(EquipmentStatus.class);
    for (var row : equipment.countByStatus()) {
      counts.put((EquipmentStatus) row[0], (Long) row[1]);
    }
    return new EquipmentOverview(
        counts.getOrDefault(EquipmentStatus.OPERATIONAL, 0L),
        counts.getOrDefault(EquipmentStatus.OUT_OF_SERVICE, 0L),
        counts.getOrDefault(EquipmentStatus.UNDER_MAINTENANCE, 0L),
        counts.getOrDefault(EquipmentStatus.RETIRED, 0L),
        faults.open().size(),
        equipment.dueBy(support.today().plusDays(30)).size());
  }

  @Transactional
  public EquipmentResponse create(EquipmentRequest r) {
    var user = support.hospitalUser();
    var d = normalize(r);
    if (equipment.codeTaken(d.code(), null)) {
      throw codeInUse(d.code());
    }
    var e = equipment.saveAndFlush(new Equipment(d));
    var response = support.toResponse(e);
    audit.record(
        entry(AuditAction.CREATE, e, user, "Equipamento registado: " + EquipmentSupport.label(e))
            .withChanges(audit.snapshot(summaryOf(response))));
    return response;
  }

  @Transactional
  public EquipmentResponse update(UUID id, EquipmentRequest r) {
    var user = support.hospitalUser();
    var e = support.lock(id);
    if (r.version() != null && !r.version().equals(e.getVersion())) {
      throw new ConflictException(
          "CONCURRENT_MODIFICATION", "O equipamento foi alterado por outra pessoa. Actualize.");
    }
    var d = normalize(r);
    if (equipment.codeTaken(d.code(), id)) {
      throw codeInUse(d.code());
    }
    var before = summaryOf(support.toResponse(e));
    e.apply(d);
    equipment.saveAndFlush(e);
    var response = support.toResponse(e);
    audit.record(
        entry(AuditAction.UPDATE, e, user, "Equipamento editado: " + EquipmentSupport.label(e))
            .withChanges(audit.diff(before, summaryOf(response))));
    return response;
  }

  @Transactional
  public EquipmentResponse sendToMaintenance(UUID id) {
    var user = support.hospitalUser();
    var e = support.lock(id);
    e.sendToMaintenance();
    equipment.saveAndFlush(e);
    audit.record(
        entry(
            AuditAction.UPDATE, e, user, "Enviado para manutenção: " + EquipmentSupport.label(e)));
    return support.toResponse(e);
  }

  @Transactional
  public EquipmentResponse retire(UUID id, String reason) {
    var user = support.hospitalUser();
    var e = support.lock(id);
    e.retire(reason.strip(), user.id(), support.now());
    equipment.saveAndFlush(e);
    audit.record(
        entry(
            AuditAction.RETIRE,
            e,
            user,
            "Equipamento abatido (" + reason.strip() + "): " + EquipmentSupport.label(e)));
    return support.toResponse(e);
  }

  private EquipmentDetails normalize(EquipmentRequest r) {
    if (r.wardId() != null && wards.activeName(r.wardId()).isEmpty()) {
      throw new BadRequestException(
          "WARD_NOT_FOUND", "A enfermaria não existe ou está desactivada.");
    }
    if (r.nextMaintenanceDate() != null && r.maintenanceIntervalDays() == null) {
      throw new BadRequestException(
          "INTERVAL_REQUIRED", "Indique a periodicidade da manutenção para calcular as seguintes.");
    }
    if (r.nextCalibrationDate() != null && r.calibrationIntervalDays() == null) {
      throw new BadRequestException(
          "INTERVAL_REQUIRED", "Indique a periodicidade da calibração para calcular as seguintes.");
    }
    var today = support.today();
    if (r.purchaseDate() != null && r.purchaseDate().isAfter(today)) {
      throw new BadRequestException("INVALID_DATE", "A data de compra não pode ser no futuro.");
    }
    return new EquipmentDetails(
        r.code().strip().toUpperCase(),
        r.name().strip(),
        r.category(),
        r.criticality(),
        EquipmentSupport.optional(r.brand()),
        EquipmentSupport.optional(r.model()),
        EquipmentSupport.optional(r.serialNumber()),
        r.wardId(),
        EquipmentSupport.optional(r.location()),
        r.purchaseDate(),
        r.purchasePrice(),
        r.warrantyUntil(),
        r.maintenanceIntervalDays(),
        r.nextMaintenanceDate() != null
            ? r.nextMaintenanceDate()
            : r.maintenanceIntervalDays() != null
                ? today.plusDays(r.maintenanceIntervalDays())
                : null,
        r.calibrationIntervalDays(),
        r.nextCalibrationDate() != null
            ? r.nextCalibrationDate()
            : r.calibrationIntervalDays() != null
                ? today.plusDays(r.calibrationIntervalDays())
                : null,
        EquipmentSupport.optional(r.notes()));
  }

  /** Só os dados do equipamento (sem histórico), para a auditoria. */
  private static EquipmentResponse summaryOf(EquipmentResponse r) {
    return new EquipmentResponse(
        r.id(),
        r.code(),
        r.name(),
        r.category(),
        r.criticality(),
        r.brand(),
        r.model(),
        r.serialNumber(),
        r.wardId(),
        r.wardName(),
        r.location(),
        r.status(),
        r.purchaseDate(),
        r.purchasePrice(),
        r.warrantyUntil(),
        r.underWarranty(),
        r.maintenanceIntervalDays(),
        r.nextMaintenanceDate(),
        r.maintenanceOverdue(),
        r.calibrationIntervalDays(),
        r.nextCalibrationDate(),
        r.calibrationOverdue(),
        r.notes(),
        r.retiredAt(),
        r.retiredByName(),
        r.retirementReason(),
        List.of(),
        List.of(),
        r.maintenanceCostTotal(),
        r.version());
  }

  private static ConflictException codeInUse(String code) {
    return new ConflictException(
        "EQUIPMENT_CODE_IN_USE", "Já existe um equipamento com o código " + code + ".");
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  static AuditEntry entry(AuditAction action, Equipment e, CurrentUser user, String summary) {
    return AuditEntry.success(
        action, AuditEntityType.EQUIPMENT, e.getId(), user.hospitalId(), summary);
  }
}
