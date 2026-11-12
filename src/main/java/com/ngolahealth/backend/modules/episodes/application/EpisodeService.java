package com.ngolahealth.backend.modules.episodes.application;

import com.ngolahealth.backend.common.clinical.Vitals;
import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.ClinicalNotesRequest;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.CreateEpisodeRequest;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.EpisodeResponse;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.EpisodeSummary;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.RescheduleRequest;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.VitalsRequest;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.VitalsResponse;
import com.ngolahealth.backend.modules.episodes.domain.Episode;
import com.ngolahealth.backend.modules.episodes.domain.EpisodeRepository;
import com.ngolahealth.backend.modules.episodes.domain.EpisodeStatus;
import com.ngolahealth.backend.modules.episodes.domain.EpisodeType;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory.PatientRef;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.CurrentUserProvider;
import com.ngolahealth.backend.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Episódios clínicos. As regras de estado vivem em {@link Episode}; aqui valida-se paciente e
 * médico, regista-se a auditoria e completa-se a resposta com nomes.
 */
@Service
@RequiredArgsConstructor
public class EpisodeService {

  static final String NOT_FOUND = "EPISODE_NOT_FOUND";

  private final EpisodeRepository episodes;
  private final PatientDirectory patients;
  private final StaffDirectory staff;
  private final CurrentUserProvider currentUser;
  private final AuditTrail audit;
  private final Clock clock;

  public record EpisodeSearch(
      Set<EpisodeStatus> statuses,
      UUID patientId,
      UUID doctorId,
      EpisodeType type,
      Instant from,
      Instant to,
      boolean newestFirst) {}

  @Transactional(readOnly = true)
  public PageResponse<EpisodeSummary> search(EpisodeSearch q, int page, int size) {
    requireHospitalUser();
    var statuses =
        q.statuses() == null || q.statuses().isEmpty()
            ? EnumSet.allOf(EpisodeStatus.class)
            : q.statuses();
    var sort =
        q.newestFirst()
            ? Sort.by(Sort.Order.desc("scheduledAt"))
            : Sort.by(Sort.Order.asc("scheduledAt"));
    var result =
        episodes.search(
            statuses,
            q.patientId(),
            q.doctorId(),
            q.type(),
            q.from(),
            q.to(),
            PageRequest.of(page, size, sort.and(Sort.by("id"))));
    var patientRefs =
        patients.findAll(result.getContent().stream().map(Episode::getPatientId).toList());
    var names = staff.names(result.getContent().stream().map(Episode::getDoctorId).toList());
    return PageResponse.of(result, e -> toSummary(e, patientRefs, names));
  }

  @Transactional
  public EpisodeResponse view(UUID id) {
    requireHospitalUser();
    var episode = load(id);
    var response = toResponse(episode);
    audit.record(entry(AuditAction.VIEW, episode, "Episódio consultado: " + describe(response)));
    return response;
  }

  @Transactional
  public EpisodeResponse create(CreateEpisodeRequest request) {
    var user = requireHospitalUser();
    var patient =
        patients
            .find(request.patientId())
            .orElseThrow(
                () -> new BadRequestException("PATIENT_NOT_FOUND", "O paciente não existe."));
    if (!patient.active()) {
      throw new ConflictException(
          "PATIENT_INACTIVE", "O paciente está inactivo; reactive-o antes de abrir um episódio.");
    }
    validateDoctor(request.doctorId(), user);
    var episode =
        episodes.saveAndFlush(
            new Episode(
                patient.id(),
                request.type(),
                request.doctorId(),
                request.scheduledAt() != null ? request.scheduledAt() : clock.instant(),
                request.reason().strip(),
                user.id()));
    var response = toResponse(episode);
    audit.record(
        entry(AuditAction.CREATE, episode, "Episódio aberto: " + describe(response))
            .withChanges(audit.snapshot(response)));
    return response;
  }

  @Transactional
  public EpisodeResponse reschedule(UUID id, RescheduleRequest request) {
    var user = requireHospitalUser();
    var episode = load(id);
    checkVersion(episode, request.version());
    validateDoctor(request.doctorId(), user);
    var before = toResponse(episode);
    episode.reschedule(
        request.type(), request.doctorId(), request.scheduledAt(), request.reason().strip());
    return saveAndAudit(episode, before, AuditAction.UPDATE, "Episódio reagendado: ");
  }

  @Transactional
  public EpisodeResponse assignDoctor(UUID id, UUID doctorId) {
    var user = requireHospitalUser();
    var episode = load(id);
    validateDoctor(doctorId, user);
    var before = toResponse(episode);
    episode.assignDoctor(doctorId);
    return saveAndAudit(episode, before, AuditAction.UPDATE, "Médico responsável alterado: ");
  }

  @Transactional
  public EpisodeResponse start(UUID id) {
    var user = requireHospitalUser();
    var episode = load(id);
    var before = toResponse(episode);
    episode.start(user.id(), clock.instant());
    return saveAndAudit(episode, before, AuditAction.START, "Episódio iniciado: ");
  }

  @Transactional
  public EpisodeResponse recordVitals(UUID id, VitalsRequest r) {
    var user = requireHospitalUser();
    var episode = load(id);
    checkVersion(episode, r.version());
    var vitals =
        new Vitals(
            r.systolicBp(),
            r.diastolicBp(),
            r.heartRate(),
            r.respiratoryRate(),
            r.temperature(),
            r.oxygenSaturation(),
            r.weightKg(),
            r.heightCm());
    vitals.validate();
    var before = toResponse(episode);
    episode.recordVitals(vitals, user.id(), clock.instant());
    return saveAndAudit(episode, before, AuditAction.UPDATE, "Sinais vitais registados: ");
  }

  @Transactional
  public EpisodeResponse recordClinicalNotes(UUID id, ClinicalNotesRequest r) {
    requireHospitalUser();
    var episode = load(id);
    checkVersion(episode, r.version());
    var before = toResponse(episode);
    episode.recordClinicalNotes(
        optional(r.symptoms()), optional(r.diagnosis()), optional(r.clinicalNotes()));
    return saveAndAudit(episode, before, AuditAction.UPDATE, "Informação clínica registada: ");
  }

  @Transactional
  public EpisodeResponse complete(UUID id) {
    var user = requireHospitalUser();
    var episode = load(id);
    var before = toResponse(episode);
    episode.complete(user.id(), clock.instant());
    return saveAndAudit(episode, before, AuditAction.COMPLETE, "Episódio concluído: ");
  }

  /**
   * Cancela. Um episódio já iniciado contém actos clínicos: só o médico o pode cancelar (a recepção
   * e a enfermagem cancelam apenas agendamentos).
   */
  @Transactional
  public EpisodeResponse cancel(UUID id, String reason) {
    var user = requireHospitalUser();
    var episode = load(id);
    if (episode.getStatus() == EpisodeStatus.IN_PROGRESS && !user.hasRole(Role.DOCTOR)) {
      throw new ForbiddenException(
          "ACCESS_DENIED", "Só um médico pode cancelar um episódio já iniciado.");
    }
    var before = toResponse(episode);
    episode.cancel(reason.strip(), user.id(), clock.instant());
    return saveAndAudit(episode, before, AuditAction.CANCEL, "Episódio cancelado: ");
  }

  private EpisodeResponse saveAndAudit(
      Episode episode, EpisodeResponse before, AuditAction action, String summaryPrefix) {
    var after = toResponse(episodes.saveAndFlush(episode));
    audit.record(
        entry(action, episode, summaryPrefix + describe(after))
            .withChanges(audit.diff(before, after)));
    return after;
  }

  private AuditEntry entry(AuditAction action, Episode episode, String summary) {
    return AuditEntry.success(
        action, AuditEntityType.EPISODE, episode.getId(), episode.getHospitalId(), summary);
  }

  private static String describe(EpisodeResponse e) {
    return e.patientName() + " (processo " + e.patientRecordNumber() + ") — " + e.reason();
  }

  private void validateDoctor(UUID doctorId, CurrentUser user) {
    if (doctorId != null && staff.findActive(doctorId, user.hospitalId(), Role.DOCTOR).isEmpty()) {
      throw new BadRequestException(
          "INVALID_DOCTOR", "O médico indicado não existe, está inactivo ou é de outro hospital.");
    }
  }

  private static void checkVersion(Episode episode, Long version) {
    if (version != null && version != episode.getVersion()) {
      throw new ConflictException(
          "CONCURRENT_MODIFICATION",
          "O episódio foi alterado por outra pessoa. Actualize e tente novamente.");
    }
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

  private Episode load(UUID id) {
    return episodes
        .findScoped(id)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND, "Episódio não encontrado."));
  }

  private EpisodeResponse toResponse(Episode e) {
    var patient = patients.find(e.getPatientId()).orElse(null);
    Set<UUID> people = new HashSet<>();
    Stream.of(
            e.getDoctorId(),
            e.getVitalsRecordedBy(),
            e.getStartedBy(),
            e.getCompletedBy(),
            e.getCancelledBy())
        .filter(java.util.Objects::nonNull)
        .forEach(people::add);
    Map<UUID, String> names = staff.names(people);
    var v = e.getVitals();
    return new EpisodeResponse(
        e.getId(),
        e.getPatientId(),
        patient == null ? null : patient.fullName(),
        patient == null ? null : patient.recordNumber(),
        e.getType(),
        e.getStatus(),
        e.getScheduledAt(),
        e.getReason(),
        e.getDoctorId(),
        nameOf(names, e.getDoctorId()),
        e.getSymptoms(),
        e.getDiagnosis(),
        e.getClinicalNotes(),
        v == null
            ? null
            : new VitalsResponse(
                v.systolicBp(),
                v.diastolicBp(),
                v.heartRate(),
                v.respiratoryRate(),
                v.temperature(),
                v.oxygenSaturation(),
                v.weightKg(),
                v.heightCm(),
                e.getVitalsRecordedAt(),
                nameOf(names, e.getVitalsRecordedBy())),
        e.getStartedAt(),
        nameOf(names, e.getStartedBy()),
        e.getCompletedAt(),
        nameOf(names, e.getCompletedBy()),
        e.getCancelledAt(),
        nameOf(names, e.getCancelledBy()),
        e.getCancellationReason(),
        e.getVersion(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }

  private static EpisodeSummary toSummary(
      Episode e, Map<UUID, PatientRef> patients, Map<UUID, String> doctors) {
    var p = patients.get(e.getPatientId());
    return new EpisodeSummary(
        e.getId(),
        e.getPatientId(),
        p == null ? null : p.fullName(),
        p == null ? null : p.recordNumber(),
        e.getType(),
        e.getStatus(),
        e.getScheduledAt(),
        e.getReason(),
        e.getDoctorId(),
        nameOf(doctors, e.getDoctorId()));
  }

  /** Nome de um profissional; null se o id for null (os mapas imutáveis recusam get(null)). */
  private static String nameOf(Map<UUID, String> names, UUID id) {
    return id == null ? null : names.get(id);
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
