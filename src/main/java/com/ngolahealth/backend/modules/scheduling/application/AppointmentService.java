package com.ngolahealth.backend.modules.scheduling.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.config.properties.SchedulingProperties;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.CreateEpisodeRequest;
import com.ngolahealth.backend.modules.episodes.application.EpisodeService;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.AppointmentRequest;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.AppointmentResponse;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.RescheduleRequest;
import com.ngolahealth.backend.modules.scheduling.domain.Appointment;
import com.ngolahealth.backend.modules.scheduling.domain.AppointmentRepository;
import com.ngolahealth.backend.modules.scheduling.domain.AppointmentStatus;
import com.ngolahealth.backend.modules.scheduling.domain.AppointmentType;
import com.ngolahealth.backend.modules.scheduling.domain.DoctorScheduleRepository;
import com.ngolahealth.backend.modules.scheduling.domain.ScheduleBlockRepository;
import com.ngolahealth.backend.security.CurrentUser;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marcações: pesquisa, marcação, confirmação, chegada, falta, cancelamento e remarcação. */
@Service
@RequiredArgsConstructor
public class AppointmentService {

  private final AppointmentRepository appointments;
  private final DoctorScheduleRepository schedules;
  private final ScheduleBlockRepository blocks;
  private final PatientDirectory patients;
  private final EpisodeService episodes;
  private final SchedulingSupport support;
  private final SchedulingProperties properties;
  private final AuditTrail audit;
  private final ApplicationEventPublisher events;

  @Transactional(readOnly = true)
  public PageResponse<AppointmentResponse> search(
      LocalDate from,
      LocalDate to,
      UUID doctorId,
      UUID patientId,
      AppointmentStatus status,
      int page,
      int size) {
    support.hospitalUser();
    var start = from != null ? from : support.today();
    var end = to != null ? to : start;
    ScheduleService.requireRange(start, end, 366);
    var result =
        appointments.search(
            start,
            end,
            doctorId,
            patientId,
            status == null ? EnumSet.allOf(AppointmentStatus.class) : EnumSet.of(status),
            PageRequest.of(
                page, size, Sort.by("date", "startTime", "slotPosition").and(Sort.by("id"))));
    var mapped = support.toResponses(result.getContent());
    var byId = new java.util.HashMap<UUID, AppointmentResponse>();
    mapped.forEach(r -> byId.put(r.id(), r));
    return PageResponse.of(result, a -> byId.get(a.getId()));
  }

  @Transactional(readOnly = true)
  public AppointmentResponse view(UUID id) {
    support.hospitalUser();
    return support.toResponse(load(id));
  }

  @Transactional
  public AppointmentResponse create(AppointmentRequest r) {
    var user = support.hospitalUser();
    var a =
        book(
            r.patientId(),
            r.doctorId(),
            r.type(),
            r.date(),
            r.startTime(),
            r.reason().strip(),
            blankToNull(r.notes()),
            null,
            user);
    var response = support.toResponse(a);
    audit.record(
        entry(AuditAction.CREATE, a, "Consulta marcada: " + SchedulingSupport.describe(response))
            .withChanges(audit.snapshot(response)));
    return response;
  }

  @Transactional
  public AppointmentResponse confirm(UUID id) {
    var user = support.hospitalUser();
    var a = lock(id);
    a.confirm(user.id(), support.instant());
    return save(a, AuditAction.CONFIRM, "Marcação confirmada: ");
  }

  /** O paciente chegou: abre o episódio (agendado, com o médico) e fecha a marcação. */
  @Transactional
  public AppointmentResponse checkIn(UUID id) {
    var user = support.hospitalUser();
    var a = lock(id);
    a.requirePending();
    var today = support.today();
    if (!a.getDate().equals(today)) {
      throw new ConflictException(
          "NOT_APPOINTMENT_DAY", "A chegada só se regista no dia da marcação.");
    }
    var episode =
        episodes.create(
            new CreateEpisodeRequest(
                a.getPatientId(),
                a.getType().episodeType(),
                a.getDoctorId(),
                support.instantOf(a.getDate(), a.getStartTime()),
                a.getReason()));
    a.checkIn(today, episode.id(), user.id(), support.instant());
    return save(a, AuditAction.CHECK_IN, "Chegada registada (episódio aberto): ");
  }

  @Transactional
  public AppointmentResponse noShow(UUID id) {
    var user = support.hospitalUser();
    var a = lock(id);
    a.markNoShow(support.now(), user.id(), support.instant());
    return save(a, AuditAction.NO_SHOW, "Falta registada: ");
  }

  @Transactional
  public AppointmentResponse cancel(UUID id, String reason) {
    var user = support.hospitalUser();
    var a = lock(id);
    a.cancel(reason.strip(), user.id(), support.instant());
    var response = save(a, AuditAction.CANCEL, "Marcação cancelada (" + reason.strip() + "): ");
    if (!user.id().equals(a.getDoctorId())) {
      events.publishEvent(
          new AppointmentEvents.Cancelled(
              a.getHospitalId(),
              a.getId(),
              a.getDoctorId(),
              patientLabel(response),
              a.getDate(),
              a.getStartTime(),
              reason.strip()));
    }
    return response;
  }

  /**
   * Nova marcação no lugar da antiga, numa só transacção: se a nova vaga falhar, a antiga
   * mantém-se.
   */
  @Transactional
  public AppointmentResponse reschedule(UUID id, RescheduleRequest r) {
    var user = support.hospitalUser();
    var old = lock(id);
    old.requirePending();
    var doctorId = r.doctorId() != null ? r.doctorId() : old.getDoctorId();
    String reason =
        "Remarcada para "
            + r.date().format(SchedulingSupport.DATE)
            + " às "
            + r.startTime()
            + (r.reason() == null || r.reason().isBlank() ? "" : " (" + r.reason().strip() + ")");
    // Liberta a vaga e o horário do paciente antes de marcar a nova (pode ser a mesma hora).
    old.cancel(reason, user.id(), support.instant());
    appointments.saveAndFlush(old);
    var created =
        book(
            old.getPatientId(),
            doctorId,
            old.getType(),
            r.date(),
            r.startTime(),
            old.getReason(),
            old.getNotes(),
            old.getId(),
            user);
    old.rescheduledTo(created.getId());
    var previous = support.toResponse(appointments.saveAndFlush(old));
    var response = support.toResponse(created);
    audit.record(
        entry(
            AuditAction.RESCHEDULE,
            created,
            "Consulta remarcada de "
                + previous.date().format(SchedulingSupport.DATE)
                + " "
                + previous.startTime()
                + " ("
                + previous.doctorName()
                + "): "
                + SchedulingSupport.describe(response)));
    events.publishEvent(
        new AppointmentEvents.Rescheduled(
            created.getHospitalId(),
            user.id(),
            created.getId(),
            old.getDoctorId(),
            doctorId,
            patientLabel(response),
            old.getDate(),
            old.getStartTime(),
            created.getDate(),
            created.getStartTime()));
    return response;
  }

  // ---------- Marcação ----------

  private Appointment book(
      UUID patientId,
      UUID doctorId,
      AppointmentType type,
      LocalDate date,
      LocalTime start,
      String reason,
      String notes,
      UUID rescheduledFrom,
      CurrentUser user) {
    var patient =
        patients
            .find(patientId)
            .orElseThrow(
                () -> new BadRequestException("PATIENT_NOT_FOUND", "O paciente não existe."));
    if (!patient.active()) {
      throw new ConflictException(
          "PATIENT_INACTIVE", "O paciente está inactivo; reactive-o antes de marcar.");
    }
    support.requireDoctor(doctorId, user);
    var now = support.now();
    if (date.isAfter(now.toLocalDate().plusDays(properties.maxDaysAhead()))) {
      throw new BadRequestException(
          "TOO_FAR_AHEAD",
          "Só se marca até " + properties.maxDaysAhead() + " dias de antecedência.");
    }
    // Bloqueio por médico e dia da semana: marcações concorrentes ficam em fila.
    var schedule =
        schedules.lockFor(doctorId, date.getDayOfWeek().getValue()).stream()
            .filter(s -> s.isSlotStart(start))
            .findFirst()
            .orElseThrow(
                () ->
                    new ConflictException(
                        "SLOT_UNAVAILABLE", "O médico não tem consulta nesse dia e hora."));
    if (!LocalDateTime.of(date, start).isAfter(now)) {
      throw new ConflictException("SLOT_IN_PAST", "Essa vaga já passou.");
    }
    var end = start.plusMinutes(schedule.getSlotMinutes());
    var block =
        blocks.between(doctorId, date, date).stream().filter(b -> b.covers(start, end)).findFirst();
    if (block.isPresent()) {
      throw new ConflictException(
          "SLOT_BLOCKED", "O médico está ausente nessa hora (" + block.get().getReason() + ").");
    }
    var clash =
        appointments.lockForPatientOn(patientId, date, AppointmentRepository.OCCUPYING).stream()
            .filter(a -> a.overlaps(start, end))
            .findFirst();
    if (clash.isPresent()) {
      var other = support.toResponse(clash.get());
      throw new ConflictException(
          "PATIENT_BUSY",
          "O paciente já tem marcação às "
              + other.startTime()
              + " com "
              + other.doctorName()
              + ".");
    }
    var taken =
        appointments.lockInSlot(doctorId, date, start, AppointmentRepository.OCCUPYING).stream()
            .map(Appointment::getSlotPosition)
            .collect(java.util.stream.Collectors.toSet());
    int position = 0;
    for (int p = 1; p <= schedule.getCapacity(); p++) {
      if (!taken.contains(p)) {
        position = p;
        break;
      }
    }
    if (position == 0) {
      throw new ConflictException("SLOT_FULL", "Essa vaga já está preenchida.");
    }
    try {
      return appointments.saveAndFlush(
          new Appointment(
              patientId,
              doctorId,
              type,
              date,
              start,
              end,
              position,
              reason,
              notes,
              rescheduledFrom,
              user.id(),
              support.instant()));
    } catch (DataIntegrityViolationException e) {
      // Rede de segurança da BD (índices únicos): nunca deve acontecer com o bloqueio acima.
      throw new ConflictException(
          "SLOT_TAKEN", "A vaga acabou de ser ocupada. Escolha outra hora.");
    }
  }

  // ---------- Auxiliares ----------

  private AppointmentResponse save(Appointment a, AuditAction action, String prefix) {
    var response = support.toResponse(appointments.saveAndFlush(a));
    audit.record(entry(action, a, prefix + SchedulingSupport.describe(response)));
    return response;
  }

  private Appointment load(UUID id) {
    return appointments
        .findScoped(id)
        .orElseThrow(
            () -> new NotFoundException("APPOINTMENT_NOT_FOUND", "Marcação não encontrada."));
  }

  private Appointment lock(UUID id) {
    return appointments
        .findForUpdate(id)
        .orElseThrow(
            () -> new NotFoundException("APPOINTMENT_NOT_FOUND", "Marcação não encontrada."));
  }

  private static String patientLabel(AppointmentResponse r) {
    return r.patientName() + " (processo " + r.patientRecordNumber() + ")";
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  private static AuditEntry entry(AuditAction action, Appointment a, String summary) {
    return AuditEntry.success(
        action, AuditEntityType.APPOINTMENT, a.getId(), a.getHospitalId(), summary);
  }
}
