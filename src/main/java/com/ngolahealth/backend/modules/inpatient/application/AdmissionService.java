package com.ngolahealth.backend.modules.inpatient.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.episodes.application.EpisodeDirectory;
import com.ngolahealth.backend.modules.episodes.application.EpisodeService;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.AdmissionResponse;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.AdmitRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.DischargeRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.TransferRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.TransferView;
import com.ngolahealth.backend.modules.inpatient.domain.Admission;
import com.ngolahealth.backend.modules.inpatient.domain.AdmissionNumbers;
import com.ngolahealth.backend.modules.inpatient.domain.AdmissionRepository;
import com.ngolahealth.backend.modules.inpatient.domain.AdmissionStatus;
import com.ngolahealth.backend.modules.inpatient.domain.AdmissionTransfer;
import com.ngolahealth.backend.modules.inpatient.domain.AdmissionTransferRepository;
import com.ngolahealth.backend.modules.inpatient.domain.Bed;
import com.ngolahealth.backend.modules.inpatient.domain.BedRepository;
import com.ngolahealth.backend.modules.inpatient.domain.Ward;
import com.ngolahealth.backend.modules.inpatient.domain.WardRepository;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory.PatientRef;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.Role;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo do internamento: internar (abre o episódio de internamento e ocupa a cama), transferir, dar
 * alta (conclui o episódio e liberta a cama para limpeza) e anular.
 */
@Service
@RequiredArgsConstructor
public class AdmissionService {

  private final AdmissionRepository admissions;
  private final AdmissionTransferRepository transfers;
  private final AdmissionNumbers numbers;
  private final BedRepository beds;
  private final WardRepository wards;
  private final PatientDirectory patients;
  private final EpisodeDirectory episodeDirectory;
  private final EpisodeService episodes;
  private final StaffDirectory staff;
  private final InpatientSupport support;
  private final AuditTrail audit;
  private final ApplicationEventPublisher events;

  // ---------- Consulta ----------

  @Transactional(readOnly = true)
  public PageResponse<AdmissionResponse> search(
      AdmissionStatus status, UUID wardId, UUID patientId, UUID doctorId, int page, int size) {
    support.hospitalUser();
    var result =
        admissions.search(
            status == null ? EnumSet.allOf(AdmissionStatus.class) : EnumSet.of(status),
            wardId,
            patientId,
            doctorId,
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("admittedAt"), Sort.Order.asc("id"))));
    var mapped =
        toResponses(result.getContent(), false).stream()
            .collect(Collectors.toMap(AdmissionResponse::id, Function.identity()));
    return PageResponse.of(result, a -> mapped.get(a.getId()));
  }

  @Transactional
  public AdmissionResponse view(UUID id) {
    support.hospitalUser();
    var a = load(id);
    var response = toResponses(List.of(a), true).getFirst();
    audit.record(entry(AuditAction.VIEW, a, "Internamento consultado: " + describe(response)));
    return response;
  }

  @Transactional(readOnly = true)
  public AdmissionResponse byEpisode(UUID episodeId) {
    support.hospitalUser();
    var a =
        admissions
            .findByEpisode(episodeId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "ADMISSION_NOT_FOUND", "Este episódio não pertence a um internamento."));
    return toResponses(List.of(a), true).getFirst();
  }

  // ---------- Internar ----------

  @Transactional
  public AdmissionResponse admit(AdmitRequest r) {
    var user = support.hospitalUser();
    var patient =
        patients
            .find(r.patientId())
            .orElseThrow(
                () -> new BadRequestException("PATIENT_NOT_FOUND", "O paciente não existe."));
    if (!patient.active()) {
      throw new ConflictException(
          "PATIENT_INACTIVE", "O paciente está inactivo; reactive-o antes de internar.");
    }
    var doctorId = r.responsibleDoctorId() != null ? r.responsibleDoctorId() : user.id();
    requireDoctor(doctorId, user);
    if (r.originEpisodeId() != null) {
      var origin =
          episodeDirectory
              .find(r.originEpisodeId())
              .orElseThrow(
                  () ->
                      new BadRequestException(
                          "EPISODE_NOT_FOUND", "O episódio de origem não existe."));
      if (!origin.patientId().equals(patient.id())) {
        throw new BadRequestException(
            "EPISODE_PATIENT_MISMATCH", "O episódio de origem é de outro paciente.");
      }
    }
    if (r.expectedDischargeDate() != null && r.expectedDischargeDate().isBefore(support.today())) {
      throw new BadRequestException(
          "INVALID_EXPECTED_DISCHARGE", "A alta prevista não pode ser no passado.");
    }
    var bed = lockBed(r.bedId());
    var ward = requireSuitableWard(bed, patient);
    var existing = admissions.lockActiveForPatient(patient.id());
    if (!existing.isEmpty()) {
      var e = existing.getFirst();
      throw new ConflictException(
          "PATIENT_ALREADY_ADMITTED",
          "O paciente já está internado (" + e.getNumber() + ", " + location(e) + ").");
    }
    bed.occupy();
    var episode = episodes.openInpatient(patient.id(), doctorId, r.reason().strip());
    var now = support.now();
    Admission saved;
    try {
      beds.saveAndFlush(bed);
      saved =
          admissions.saveAndFlush(
              new Admission(
                  numbers.next(user.hospitalId(), support.today().getYear()),
                  patient.id(),
                  episode.id(),
                  r.originEpisodeId(),
                  bed,
                  doctorId,
                  r.reason().strip(),
                  r.expectedDischargeDate(),
                  user.id(),
                  now));
    } catch (DataIntegrityViolationException e) {
      // Rede de segurança (índices únicos de internamento activo); o bloqueio acima evita-o.
      throw new ConflictException(
          "ADMISSION_CONFLICT",
          "A cama ou o paciente acabou de ser ocupado por outro internamento.");
    }
    var response = toResponses(List.of(saved), true).getFirst();
    audit.record(
        entry(
                AuditAction.CREATE,
                saved,
                "Paciente internado: " + describe(response) + " em " + ward.getName())
            .withChanges(audit.snapshot(response)));
    publishAssigned(saved, user, response, true);
    return response;
  }

  // ---------- Transferir ----------

  @Transactional
  public AdmissionResponse transfer(UUID id, TransferRequest r) {
    var user = support.hospitalUser();
    var a = lock(id);
    a.requireActive();
    if (a.getBedId().equals(r.toBedId())) {
      throw new BadRequestException("SAME_BED", "O paciente já está nessa cama.");
    }
    // Bloqueia as duas camas por ordem de id: dois pedidos cruzados não ficam em impasse.
    var locked = new HashMap<UUID, Bed>();
    Stream.of(a.getBedId(), r.toBedId()).sorted().forEach(b -> locked.put(b, lockBed(b)));
    var from = locked.get(a.getBedId());
    var to = locked.get(r.toBedId());
    var patient = patients.find(a.getPatientId()).orElseThrow();
    var toWard = requireSuitableWard(to, patient);
    var fromLabel = location(a);
    to.occupy();
    from.release();
    a.moveTo(to);
    transfers.save(
        new AdmissionTransfer(a.getId(), from, to, r.reason().strip(), user.id(), support.now()));
    beds.saveAndFlush(from);
    beds.saveAndFlush(to);
    var saved = admissions.saveAndFlush(a);
    var response = toResponses(List.of(saved), true).getFirst();
    audit.record(
        entry(
            AuditAction.TRANSFER,
            saved,
            "Transferência: "
                + describe(response)
                + " de "
                + fromLabel
                + " para "
                + toWard.getName()
                + ", cama "
                + to.getCode()
                + " ("
                + r.reason().strip()
                + ")"));
    return response;
  }

  // ---------- Alta e anulação ----------

  @Transactional
  public AdmissionResponse discharge(UUID id, DischargeRequest r) {
    var user = support.hospitalUser();
    var a = lock(id);
    var bed = lockBed(a.getBedId());
    a.discharge(
        r.condition(),
        r.diagnosis().strip(),
        InpatientSupport.optional(r.summary()),
        user.id(),
        support.now());
    bed.release();
    beds.saveAndFlush(bed);
    var saved = admissions.saveAndFlush(a);
    episodes.closeInpatient(a.getEpisodeId(), r.diagnosis().strip());
    var response = toResponses(List.of(saved), true).getFirst();
    audit.record(
        entry(
            AuditAction.DISCHARGE,
            saved,
            "Alta ("
                + r.condition()
                + "): "
                + describe(response)
                + " após "
                + response.lengthOfStayDays()
                + " dia(s)"));
    return response;
  }

  @Transactional
  public AdmissionResponse cancel(UUID id, String reason) {
    var user = support.hospitalUser();
    var a = lock(id);
    var bed = lockBed(a.getBedId());
    a.cancel(reason.strip(), user.id(), support.now());
    bed.release();
    beds.saveAndFlush(bed);
    var saved = admissions.saveAndFlush(a);
    episodes.cancelInpatient(a.getEpisodeId(), "Internamento anulado: " + reason.strip());
    var response = toResponses(List.of(saved), true).getFirst();
    audit.record(
        entry(
            AuditAction.CANCEL,
            saved,
            "Internamento anulado (" + reason.strip() + "): " + describe(response)));
    return response;
  }

  @Transactional
  public AdmissionResponse assignDoctor(UUID id, UUID doctorId) {
    var user = support.hospitalUser();
    requireDoctor(doctorId, user);
    var a = lock(id);
    a.assignDoctor(doctorId);
    var saved = admissions.saveAndFlush(a);
    var response = toResponses(List.of(saved), true).getFirst();
    audit.record(
        entry(
            AuditAction.UPDATE,
            saved,
            "Médico responsável: "
                + response.responsibleDoctorName()
                + " — "
                + describe(response)));
    publishAssigned(saved, user, response, false);
    return response;
  }

  @Transactional
  public AdmissionResponse setExpectedDischarge(UUID id, java.time.LocalDate date) {
    support.hospitalUser();
    if (date != null && date.isBefore(support.today())) {
      throw new BadRequestException(
          "INVALID_EXPECTED_DISCHARGE", "A alta prevista não pode ser no passado.");
    }
    var a = lock(id);
    a.setExpectedDischargeDate(date);
    var saved = admissions.saveAndFlush(a);
    var response = toResponses(List.of(saved), true).getFirst();
    audit.record(
        entry(AuditAction.UPDATE, saved, "Alta prevista: " + date + " — " + describe(response)));
    return response;
  }

  // ---------- Auxiliares ----------

  private Ward requireSuitableWard(Bed bed, PatientRef patient) {
    var ward =
        wards
            .findScoped(bed.getWardId())
            .orElseThrow(
                () -> new NotFoundException("WARD_NOT_FOUND", "Enfermaria não encontrada."));
    if (!ward.isActive()) {
      throw new ConflictException(
          "WARD_INACTIVE", "A enfermaria " + ward.getName() + " está desactivada.");
    }
    if (patient.gender() != null && !ward.getGenderPolicy().accepts(patient.gender())) {
      throw new ConflictException(
          "WARD_GENDER_MISMATCH",
          "A enfermaria " + ward.getName() + " não recebe doentes deste sexo.");
    }
    return ward;
  }

  private void requireDoctor(UUID doctorId, CurrentUser user) {
    if (staff.findActive(doctorId, user.hospitalId(), Role.DOCTOR).isEmpty()) {
      throw new BadRequestException(
          "INVALID_DOCTOR", "O médico indicado não existe, está inactivo ou é de outro hospital.");
    }
  }

  private void publishAssigned(
      Admission a, CurrentUser user, AdmissionResponse response, boolean newAdmission) {
    events.publishEvent(
        new AdmissionEvents.DoctorAssigned(
            a.getHospitalId(),
            a.getId(),
            a.getResponsibleDoctorId(),
            user.id(),
            response.patientName() + " (processo " + response.patientRecordNumber() + ")",
            response.wardName() + ", cama " + response.bedCode(),
            newAdmission));
  }

  private Bed lockBed(UUID id) {
    return beds.findForUpdate(id)
        .orElseThrow(() -> new NotFoundException("BED_NOT_FOUND", "Cama não encontrada."));
  }

  private Admission load(UUID id) {
    return admissions
        .findScoped(id)
        .orElseThrow(
            () -> new NotFoundException("ADMISSION_NOT_FOUND", "Internamento não encontrado."));
  }

  private Admission lock(UUID id) {
    return admissions
        .findForUpdate(id)
        .orElseThrow(
            () -> new NotFoundException("ADMISSION_NOT_FOUND", "Internamento não encontrado."));
  }

  private String location(Admission a) {
    var ward = wards.findScoped(a.getWardId()).map(Ward::getName).orElse("?");
    var bed = beds.findScoped(a.getBedId()).map(Bed::getCode).orElse("?");
    return ward + ", cama " + bed;
  }

  /** Conversão em lote: pacientes, camas, enfermarias e pessoas numa consulta cada. */
  private List<AdmissionResponse> toResponses(List<Admission> list, boolean withTransfers) {
    var patientRefs = patients.findAll(list.stream().map(Admission::getPatientId).toList());
    var transferMap = new HashMap<UUID, List<AdmissionTransfer>>();
    if (withTransfers) {
      list.forEach(a -> transferMap.put(a.getId(), transfers.forAdmission(a.getId())));
    }
    var bedIds = new HashSet<UUID>();
    var wardIds = new HashSet<UUID>();
    var people = new HashSet<UUID>();
    list.forEach(
        a -> {
          bedIds.add(a.getBedId());
          wardIds.add(a.getWardId());
          Stream.of(
                  a.getResponsibleDoctorId(),
                  a.getAdmittedBy(),
                  a.getDischargedBy(),
                  a.getCancelledBy())
              .filter(Objects::nonNull)
              .forEach(people::add);
        });
    transferMap
        .values()
        .forEach(
            ts ->
                ts.forEach(
                    t -> {
                      bedIds.add(t.getFromBedId());
                      bedIds.add(t.getToBedId());
                      wardIds.add(t.getFromWardId());
                      wardIds.add(t.getToWardId());
                      people.add(t.getTransferredBy());
                    }));
    Map<UUID, String> bedCodes =
        bedIds.isEmpty()
            ? java.util.Collections.emptyMap()
            : beds.findAllScoped(bedIds).stream()
                .collect(Collectors.toMap(Bed::getId, Bed::getCode));
    Map<UUID, String> wardNames =
        wardIds.isEmpty()
            ? java.util.Collections.emptyMap()
            : wards.findAllScoped(wardIds).stream()
                .collect(Collectors.toMap(Ward::getId, Ward::getName));
    var names = staff.names(people);
    var now = support.now();
    var result = new ArrayList<AdmissionResponse>();
    for (var a : list) {
      var p = patientRefs.get(a.getPatientId());
      var end =
          a.getDischargedAt() != null
              ? a.getDischargedAt()
              : a.getCancelledAt() != null ? a.getCancelledAt() : now;
      result.add(
          new AdmissionResponse(
              a.getId(),
              a.getNumber(),
              a.getPatientId(),
              p == null ? null : p.fullName(),
              p == null ? null : p.recordNumber(),
              p == null ? null : p.gender(),
              p == null ? null : support.age(p.birthDate()),
              a.getEpisodeId(),
              a.getOriginEpisodeId(),
              a.getWardId(),
              wardNames.get(a.getWardId()),
              a.getBedId(),
              bedCodes.get(a.getBedId()),
              a.getResponsibleDoctorId(),
              names.get(a.getResponsibleDoctorId()),
              a.getStatus(),
              a.getReason(),
              names.get(a.getAdmittedBy()),
              a.getAdmittedAt(),
              a.getExpectedDischargeDate(),
              support.daysBetween(a.getAdmittedAt(), end),
              names.get(a.getDischargedBy()),
              a.getDischargedAt(),
              a.getDischargeCondition(),
              a.getDischargeDiagnosis(),
              a.getDischargeSummary(),
              names.get(a.getCancelledBy()),
              a.getCancelledAt(),
              a.getCancellationReason(),
              transferMap.getOrDefault(a.getId(), List.of()).stream()
                  .map(
                      t ->
                          new TransferView(
                              wardNames.get(t.getFromWardId()),
                              bedCodes.get(t.getFromBedId()),
                              wardNames.get(t.getToWardId()),
                              bedCodes.get(t.getToBedId()),
                              t.getReason(),
                              names.get(t.getTransferredBy()),
                              t.getTransferredAt()))
                  .toList(),
              a.getVersion() == null ? 0 : a.getVersion()));
    }
    return result;
  }

  private static String describe(AdmissionResponse a) {
    return a.number() + " — " + a.patientName() + " (processo " + a.patientRecordNumber() + ")";
  }

  private static AuditEntry entry(AuditAction action, Admission a, String summary) {
    return AuditEntry.success(
        action, AuditEntityType.ADMISSION, a.getId(), a.getHospitalId(), summary);
  }
}
