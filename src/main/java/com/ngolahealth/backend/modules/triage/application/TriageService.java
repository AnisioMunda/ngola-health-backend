package com.ngolahealth.backend.modules.triage.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.config.properties.TriageProperties;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.CreateEpisodeRequest;
import com.ngolahealth.backend.modules.episodes.application.EpisodeService;
import com.ngolahealth.backend.modules.episodes.domain.EpisodeType;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory.PatientRef;
import com.ngolahealth.backend.modules.triage.api.TriageDtos.ChangePriorityRequest;
import com.ngolahealth.backend.modules.triage.api.TriageDtos.CreateTriageRequest;
import com.ngolahealth.backend.modules.triage.api.TriageDtos.PriorityChangeView;
import com.ngolahealth.backend.modules.triage.api.TriageDtos.TriageResponse;
import com.ngolahealth.backend.modules.triage.api.TriageDtos.TriageStats;
import com.ngolahealth.backend.modules.triage.api.TriageDtos.VitalsView;
import com.ngolahealth.backend.modules.triage.domain.PriorityChange;
import com.ngolahealth.backend.modules.triage.domain.PriorityChangeRepository;
import com.ngolahealth.backend.modules.triage.domain.TriagePriority;
import com.ngolahealth.backend.modules.triage.domain.TriageQueueNumbers;
import com.ngolahealth.backend.modules.triage.domain.TriageRecord;
import com.ngolahealth.backend.modules.triage.domain.TriageRepository;
import com.ngolahealth.backend.modules.triage.domain.TriageStatus;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Triagem de Manchester: fila, re-triagem, chamada, encaminhamento para episódio. */
@Service
@RequiredArgsConstructor
public class TriageService {

  static final String NOT_FOUND = "TRIAGE_NOT_FOUND";

  /** Fila: em espera por prioridade e chegada; depois os que estão em atendimento. */
  static final Comparator<TriageRecord> QUEUE_ORDER =
      Comparator.comparing((TriageRecord t) -> t.getStatus() != TriageStatus.WAITING)
          .thenComparing(TriageRecord::getPriority)
          .thenComparing(TriageRecord::getTriagedAt);

  private final TriageRepository triages;
  private final PriorityChangeRepository priorityChanges;
  private final TriageQueueNumbers queueNumbers;
  private final PatientDirectory patients;
  private final StaffDirectory staff;
  private final EpisodeService episodes;
  private final CurrentUserProvider currentUser;
  private final AuditTrail audit;
  private final TriageProperties properties;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<TriageResponse> activeQueue() {
    requireHospitalUser();
    var records =
        triages.findByStatusIn(List.of(TriageStatus.WAITING, TriageStatus.IN_PROGRESS)).stream()
            .sorted(QUEUE_ORDER)
            .toList();
    return toResponses(records);
  }

  @Transactional(readOnly = true)
  public List<TriageResponse> day(LocalDate day) {
    requireHospitalUser();
    return toResponses(triages.findByDay(day != null ? day : today()));
  }

  @Transactional(readOnly = true)
  public TriageStats stats() {
    requireHospitalUser();
    var today = today();
    var todays = triages.findByDay(today);
    var active = triages.findByStatusIn(List.of(TriageStatus.WAITING, TriageStatus.IN_PROGRESS));
    var now = clock.instant();
    Map<TriagePriority, Integer> byPriority = new EnumMap<>(TriagePriority.class);
    for (var p : TriagePriority.values()) {
      byPriority.put(p, 0);
    }
    int waiting = 0;
    int overdue = 0;
    for (var t : active) {
      if (t.getStatus() == TriageStatus.WAITING) {
        waiting++;
        byPriority.merge(t.getPriority(), 1, Integer::sum);
        if (isOverdue(t, now)) {
          overdue++;
        }
      }
    }
    var waits =
        todays.stream()
            .filter(t -> t.getCalledAt() != null)
            .mapToLong(t -> Duration.between(t.getTriagedAt(), t.getCalledAt()).toMinutes())
            .summaryStatistics();
    return new TriageStats(
        today,
        todays.size(),
        waiting,
        active.size() - waiting,
        overdue,
        (int) todays.stream().filter(t -> t.getStatus() == TriageStatus.LEFT).count(),
        byPriority,
        waits.getCount() == 0 ? null : (int) Math.round(waits.getAverage()));
  }

  @Transactional
  public TriageResponse view(UUID id) {
    requireHospitalUser();
    var record = load(id);
    var response = toResponse(record, true);
    audit.record(entry(AuditAction.VIEW, record, "Triagem consultada: " + describe(response)));
    return response;
  }

  @Transactional
  public TriageResponse create(CreateTriageRequest r) {
    var user = requireHospitalUser();
    if (r.patientId() != null) {
      var patient =
          patients
              .find(r.patientId())
              .orElseThrow(
                  () -> new BadRequestException("PATIENT_NOT_FOUND", "O paciente não existe."));
      if (!patient.active()) {
        throw new ConflictException("PATIENT_INACTIVE", "O paciente está inactivo.");
      }
    }
    var now = clock.instant();
    var day = today();
    var record =
        triages.saveAndFlush(
            new TriageRecord(
                day,
                queueNumbers.next(user.hospitalId(), day),
                r.patientId(),
                optional(r.tempName()),
                r.tempAge(),
                r.tempGender(),
                r.priority(),
                r.chiefComplaint().strip(),
                r.painScale(),
                optional(r.notes()),
                r.vitals() == null ? null : r.vitals().toVitals(),
                user.id(),
                now));
    var response = toResponse(record, false);
    audit.record(
        entry(AuditAction.CREATE, record, "Triagem registada: " + describe(response))
            .withChanges(audit.snapshot(response)));
    return response;
  }

  @Transactional
  public TriageResponse changePriority(UUID id, ChangePriorityRequest r) {
    var user = requireHospitalUser();
    var record = load(id);
    var from = record.getPriority();
    record.changePriority(r.priority());
    priorityChanges.save(
        new PriorityChange(id, from, r.priority(), r.reason().strip(), user.id(), clock.instant()));
    var saved = triages.saveAndFlush(record);
    var response = toResponse(saved, true);
    audit.record(
        entry(
            AuditAction.UPDATE,
            saved,
            "Re-triagem "
                + from
                + " → "
                + r.priority()
                + " ("
                + r.reason().strip()
                + "): "
                + describe(response)));
    return response;
  }

  @Transactional
  public TriageResponse identify(UUID id, UUID patientId) {
    requireHospitalUser();
    var record = load(id);
    var patient =
        patients
            .find(patientId)
            .orElseThrow(
                () -> new BadRequestException("PATIENT_NOT_FOUND", "O paciente não existe."));
    var provisional = record.getTempName();
    record.identify(patient.id());
    var saved = triages.saveAndFlush(record);
    audit.record(
        entry(
            AuditAction.UPDATE,
            saved,
            "Doente identificado: «"
                + provisional
                + "» é "
                + patient.fullName()
                + " (processo "
                + patient.recordNumber()
                + ")"));
    return toResponse(saved, true);
  }

  @Transactional
  public TriageResponse call(UUID id) {
    var user = requireHospitalUser();
    var record = load(id);
    record.call(user.id(), clock.instant());
    return saveWithEvent(record, AuditAction.START, "Doente chamado: ");
  }

  /** Conclui a triagem sem episódio (ex.: orientado para fora do hospital). */
  @Transactional
  public TriageResponse complete(UUID id) {
    var user = requireHospitalUser();
    var record = load(id);
    record.complete(null, user.id(), clock.instant());
    return saveWithEvent(record, AuditAction.COMPLETE, "Triagem concluída: ");
  }

  /**
   * Abre um episódio de urgência para o doente (acção explícita, nunca automática) e conclui a
   * triagem ligada a ele. O doente tem de estar identificado.
   */
  @Transactional
  public TriageResponse openEpisode(UUID id, UUID doctorId) {
    var user = requireHospitalUser();
    var record = load(id);
    if (record.getPatientId() == null) {
      throw new ConflictException(
          "PATIENT_NOT_IDENTIFIED",
          "Identifique o doente (associe-o ao paciente registado) antes de abrir o episódio.");
    }
    if (record.getStatus() != TriageStatus.IN_PROGRESS) {
      throw new ConflictException(
          "INVALID_TRIAGE_STATUS", "Chame o doente antes de abrir o episódio.");
    }
    var episode =
        episodes.create(
            new CreateEpisodeRequest(
                record.getPatientId(),
                EpisodeType.EMERGENCY,
                doctorId,
                clock.instant(),
                "Urgência (triagem " + record.getPriority() + "): " + record.getChiefComplaint()));
    record.complete(episode.id(), user.id(), clock.instant());
    return saveWithEvent(record, AuditAction.COMPLETE, "Encaminhado para episódio: ");
  }

  @Transactional
  public TriageResponse markLeft(UUID id) {
    var user = requireHospitalUser();
    var record = load(id);
    record.markLeft(user.id(), clock.instant());
    return saveWithEvent(record, AuditAction.CANCEL, "Saiu sem ser atendido: ");
  }

  private TriageResponse saveWithEvent(TriageRecord record, AuditAction action, String prefix) {
    var saved = triages.saveAndFlush(record);
    var response = toResponse(saved, true);
    audit.record(entry(action, saved, prefix + describe(response)));
    return response;
  }

  private AuditEntry entry(AuditAction action, TriageRecord t, String summary) {
    return AuditEntry.success(
        action, AuditEntityType.TRIAGE, t.getId(), t.getHospitalId(), summary);
  }

  private static String describe(TriageResponse t) {
    return "senha " + t.queueNumber() + ", " + t.displayName() + ", " + t.priority();
  }

  private boolean isOverdue(TriageRecord t, Instant now) {
    return t.getStatus() == TriageStatus.WAITING
        && Duration.between(t.getTriagedAt(), now).toMinutes()
            > properties.targetFor(t.getPriority());
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), properties.zone());
  }

  private CurrentUser requireHospitalUser() {
    var user = currentUser.get();
    if (user.hospitalId() == null) {
      throw new ForbiddenException(
          "HOSPITAL_SCOPE_REQUIRED",
          "Os dados clínicos só estão acessíveis a utilizadores de um hospital.");
    }
    return user;
  }

  private TriageRecord load(UUID id) {
    return triages
        .findScoped(id)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND, "Triagem não encontrada."));
  }

  private List<TriageResponse> toResponses(List<TriageRecord> records) {
    var patientRefs =
        patients.findAll(
            records.stream().map(TriageRecord::getPatientId).filter(Objects::nonNull).toList());
    var names = staff.names(people(records.stream()));
    var now = clock.instant();
    return records.stream().map(t -> build(t, patientRefs, names, List.of(), now)).toList();
  }

  private TriageResponse toResponse(TriageRecord t, boolean withHistory) {
    var changes = withHistory ? priorityChanges.findByTriage(t.getId()) : List.<PriorityChange>of();
    Set<UUID> people = new HashSet<>(people(Stream.of(t)));
    changes.forEach(c -> people.add(c.getChangedBy()));
    var names = staff.names(people);
    var patientRefs =
        t.getPatientId() == null
            ? Map.<UUID, PatientRef>of()
            : patients.findAll(List.of(t.getPatientId()));
    var history =
        changes.stream()
            .map(
                c ->
                    new PriorityChangeView(
                        c.getFromPriority(),
                        c.getToPriority(),
                        c.getReason(),
                        nameOf(names, c.getChangedBy()),
                        c.getChangedAt()))
            .toList();
    return build(t, patientRefs, names, history, clock.instant());
  }

  private TriageResponse build(
      TriageRecord t,
      Map<UUID, PatientRef> patientRefs,
      Map<UUID, String> names,
      List<PriorityChangeView> history,
      Instant now) {
    var patient = t.getPatientId() == null ? null : patientRefs.get(t.getPatientId());
    var waitEnd =
        t.getCalledAt() != null ? t.getCalledAt() : t.getClosedAt() != null ? t.getClosedAt() : now;
    var v = t.getVitals();
    return new TriageResponse(
        t.getId(),
        t.getQueueDate(),
        t.getQueueNumber(),
        t.getPatientId(),
        patient == null ? null : patient.recordNumber(),
        patient != null ? patient.fullName() : t.getTempName(),
        t.getPatientId() != null,
        t.getTempAge(),
        t.getTempGender(),
        t.getPriority(),
        t.getChiefComplaint(),
        t.getPainScale(),
        t.getNotes(),
        v == null
            ? null
            : new VitalsView(
                v.systolicBp(),
                v.diastolicBp(),
                v.heartRate(),
                v.respiratoryRate(),
                v.temperature(),
                v.oxygenSaturation(),
                v.weightKg(),
                v.heightCm()),
        t.getStatus(),
        t.getTriagedAt(),
        nameOf(names, t.getTriagedBy()),
        t.getCalledAt(),
        nameOf(names, t.getCalledBy()),
        t.getClosedAt(),
        nameOf(names, t.getClosedBy()),
        t.getEpisodeId(),
        Math.max(0, Duration.between(t.getTriagedAt(), waitEnd).toMinutes()),
        properties.targetFor(t.getPriority()),
        isOverdue(t, now),
        history,
        t.getVersion());
  }

  private static Set<UUID> people(Stream<TriageRecord> records) {
    Set<UUID> ids = new HashSet<>();
    records.forEach(
        t ->
            Stream.of(t.getTriagedBy(), t.getCalledBy(), t.getClosedBy())
                .filter(Objects::nonNull)
                .forEach(ids::add));
    return ids;
  }

  private static String nameOf(Map<UUID, String> names, UUID id) {
    return id == null ? null : names.get(id);
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
