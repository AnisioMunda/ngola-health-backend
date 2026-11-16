package com.ngolahealth.backend.modules.inpatient.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.BedRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.BedResponse;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.BedStatusRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.Occupant;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.WardMap;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.WardRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.WardResponse;
import com.ngolahealth.backend.modules.inpatient.domain.Admission;
import com.ngolahealth.backend.modules.inpatient.domain.AdmissionRepository;
import com.ngolahealth.backend.modules.inpatient.domain.Bed;
import com.ngolahealth.backend.modules.inpatient.domain.BedRepository;
import com.ngolahealth.backend.modules.inpatient.domain.BedStatus;
import com.ngolahealth.backend.modules.inpatient.domain.Ward;
import com.ngolahealth.backend.modules.inpatient.domain.WardDetails;
import com.ngolahealth.backend.modules.inpatient.domain.WardRepository;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enfermarias, camas, estados de limpeza/manutenção e mapa de ocupação. */
@Service
@RequiredArgsConstructor
public class WardService {

  private final WardRepository wards;
  private final BedRepository beds;
  private final AdmissionRepository admissions;
  private final PatientDirectory patients;
  private final StaffDirectory staff;
  private final InpatientSupport support;
  private final AuditTrail audit;

  // ---------- Enfermarias ----------

  @Transactional(readOnly = true)
  public List<WardResponse> wards(boolean includeInactive) {
    support.hospitalUser();
    var list = wards.list(includeInactive);
    var byWard = beds.list(null, false).stream().collect(Collectors.groupingBy(Bed::getWardId));
    return list.stream()
        .map(w -> toResponse(w, byWard.getOrDefault(w.getId(), List.of())))
        .toList();
  }

  @Transactional
  public WardResponse createWard(WardRequest r) {
    support.hospitalUser();
    var d = normalize(r);
    if (wards.codeTaken(d.code(), null)) {
      throw codeInUse(d.code());
    }
    var w = wards.saveAndFlush(new Ward(d));
    var response = toResponse(w, List.of());
    audit.record(
        entry(
                AuditAction.CREATE,
                AuditEntityType.WARD,
                w.getId(),
                "Enfermaria criada: " + w.getName())
            .withChanges(audit.snapshot(response)));
    return response;
  }

  @Transactional
  public WardResponse updateWard(UUID id, WardRequest r) {
    support.hospitalUser();
    var w = loadWard(id);
    if (r.version() != null && !r.version().equals(w.getVersion())) {
      throw new ConflictException(
          "CONCURRENT_MODIFICATION", "A enfermaria foi alterada por outra pessoa. Actualize.");
    }
    var d = normalize(r);
    if (wards.codeTaken(d.code(), id)) {
      throw codeInUse(d.code());
    }
    var wardBeds = beds.list(id, false);
    var before = toResponse(w, wardBeds);
    w.apply(d);
    var after = toResponse(wards.saveAndFlush(w), wardBeds);
    audit.record(
        entry(AuditAction.UPDATE, AuditEntityType.WARD, id, "Enfermaria editada: " + w.getName())
            .withChanges(audit.diff(before, after)));
    return after;
  }

  @Transactional
  public WardResponse setWardActive(UUID id, boolean active) {
    support.hospitalUser();
    var w = loadWard(id);
    var wardBeds = beds.list(id, false);
    if (!active && wardBeds.stream().anyMatch(Bed::isOccupied)) {
      throw new ConflictException(
          "WARD_HAS_PATIENTS", "A enfermaria tem doentes internados: transfira-os primeiro.");
    }
    w.setActive(active);
    var response = toResponse(wards.saveAndFlush(w), wardBeds);
    audit.record(
        entry(
            active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE,
            AuditEntityType.WARD,
            id,
            (active ? "Enfermaria activada: " : "Enfermaria desactivada: ") + w.getName()));
    return response;
  }

  // ---------- Camas ----------

  @Transactional
  public BedResponse createBed(BedRequest r) {
    support.hospitalUser();
    var w = loadWard(r.wardId());
    if (!w.isActive()) {
      throw new ConflictException("WARD_INACTIVE", "A enfermaria está desactivada.");
    }
    var code = r.code().strip().toUpperCase();
    if (beds.codeTaken(w.getId(), code, null)) {
      throw new ConflictException(
          "BED_CODE_IN_USE", "Já existe a cama " + code + " nesta enfermaria.");
    }
    var b = beds.saveAndFlush(new Bed(w.getId(), code, r.type()));
    audit.record(
        entry(
            AuditAction.CREATE,
            AuditEntityType.BED,
            b.getId(),
            "Cama " + code + " criada na enfermaria " + w.getName()));
    return toBed(b, null);
  }

  @Transactional
  public BedResponse updateBed(UUID id, BedRequest r) {
    support.hospitalUser();
    var b = loadBed(id);
    if (!b.getWardId().equals(r.wardId())) {
      throw new BadRequestException(
          "BED_WARD_CHANGE", "Uma cama não muda de enfermaria: crie-a na outra enfermaria.");
    }
    var code = r.code().strip().toUpperCase();
    if (beds.codeTaken(b.getWardId(), code, id)) {
      throw new ConflictException(
          "BED_CODE_IN_USE", "Já existe a cama " + code + " nesta enfermaria.");
    }
    b.edit(code, r.type());
    beds.saveAndFlush(b);
    audit.record(entry(AuditAction.UPDATE, AuditEntityType.BED, id, "Cama editada: " + code));
    return toBed(b, null);
  }

  /** Limpeza e manutenção; com bloqueio, para não colidir com um internamento simultâneo. */
  @Transactional
  public BedResponse setBedStatus(UUID id, BedStatusRequest r) {
    support.hospitalUser();
    var b =
        beds.findForUpdate(id)
            .orElseThrow(() -> new NotFoundException("BED_NOT_FOUND", "Cama não encontrada."));
    var previous = b.getStatus();
    b.setHousekeeping(r.status(), InpatientSupport.optional(r.note()));
    beds.saveAndFlush(b);
    audit.record(
        entry(
            AuditAction.UPDATE,
            AuditEntityType.BED,
            id,
            "Cama " + b.getCode() + ": " + previous + " → " + r.status()));
    return toBed(b, null);
  }

  @Transactional
  public BedResponse setBedActive(UUID id, boolean active) {
    support.hospitalUser();
    var b =
        beds.findForUpdate(id)
            .orElseThrow(() -> new NotFoundException("BED_NOT_FOUND", "Cama não encontrada."));
    if (active) {
      b.activate();
    } else {
      b.deactivate();
    }
    beds.saveAndFlush(b);
    audit.record(
        entry(
            active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE,
            AuditEntityType.BED,
            id,
            (active ? "Cama activada: " : "Cama desactivada: ") + b.getCode()));
    return toBed(b, null);
  }

  // ---------- Mapa ----------

  /** Mapa da enfermaria: camas, ocupantes e médicos em consultas por lote (sem N+1). */
  @Transactional(readOnly = true)
  public WardMap map(UUID wardId, boolean includeInactiveBeds) {
    support.hospitalUser();
    var w = loadWard(wardId);
    var wardBeds = beds.list(wardId, includeInactiveBeds);
    var active =
        admissions.active(wardId).stream()
            .collect(Collectors.toMap(Admission::getBedId, Function.identity()));
    var patientRefs =
        patients.findAll(active.values().stream().map(Admission::getPatientId).toList());
    var doctors =
        staff.names(active.values().stream().map(Admission::getResponsibleDoctorId).toList());
    var bedResponses =
        wardBeds.stream()
            .map(
                b -> {
                  var a = active.get(b.getId());
                  if (a == null) {
                    return toBed(b, null);
                  }
                  var p = patientRefs.get(a.getPatientId());
                  return toBed(
                      b,
                      new Occupant(
                          a.getId(),
                          a.getNumber(),
                          a.getPatientId(),
                          p == null ? null : p.fullName(),
                          p == null ? null : p.recordNumber(),
                          p == null ? null : p.gender(),
                          p == null ? null : support.age(p.birthDate()),
                          doctors.get(a.getResponsibleDoctorId()),
                          a.getAdmittedAt(),
                          a.getExpectedDischargeDate()));
                })
            .toList();
    return new WardMap(toResponse(w, beds.list(wardId, false)), bedResponses);
  }

  // ---------- Auxiliares ----------

  Ward loadWard(UUID id) {
    return wards
        .findScoped(id)
        .orElseThrow(() -> new NotFoundException("WARD_NOT_FOUND", "Enfermaria não encontrada."));
  }

  private Bed loadBed(UUID id) {
    return beds.findScoped(id)
        .orElseThrow(() -> new NotFoundException("BED_NOT_FOUND", "Cama não encontrada."));
  }

  private static WardDetails normalize(WardRequest r) {
    return new WardDetails(
        r.code().strip().toUpperCase(),
        r.name().strip(),
        r.type(),
        r.genderPolicy(),
        InpatientSupport.optional(r.floor()),
        InpatientSupport.optional(r.notes()));
  }

  private static ConflictException codeInUse(String code) {
    return new ConflictException(
        "WARD_CODE_IN_USE", "Já existe uma enfermaria com o código " + code + ".");
  }

  static WardResponse toResponse(Ward w, List<Bed> wardBeds) {
    Map<BedStatus, Long> counts =
        wardBeds.stream()
            .filter(Bed::isActive)
            .collect(Collectors.groupingBy(Bed::getStatus, Collectors.counting()));
    return new WardResponse(
        w.getId(),
        w.getCode(),
        w.getName(),
        w.getType(),
        w.getGenderPolicy(),
        w.getFloor(),
        w.getNotes(),
        w.isActive(),
        (int) wardBeds.stream().filter(Bed::isActive).count(),
        counts.getOrDefault(BedStatus.AVAILABLE, 0L).intValue(),
        counts.getOrDefault(BedStatus.OCCUPIED, 0L).intValue(),
        counts.getOrDefault(BedStatus.CLEANING, 0L).intValue(),
        counts.getOrDefault(BedStatus.MAINTENANCE, 0L).intValue(),
        w.getVersion() == null ? 0 : w.getVersion());
  }

  static BedResponse toBed(Bed b, Occupant occupant) {
    return new BedResponse(
        b.getId(),
        b.getWardId(),
        b.getCode(),
        b.getType(),
        b.getStatus(),
        b.getStatusNote(),
        b.isActive(),
        occupant);
  }

  private AuditEntry entry(AuditAction action, AuditEntityType type, UUID id, String summary) {
    return AuditEntry.success(action, type, id, support.hospitalUser().hospitalId(), summary);
  }
}
