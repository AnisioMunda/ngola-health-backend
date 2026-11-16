package com.ngolahealth.backend.modules.scheduling.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.config.properties.SchedulingProperties;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.BlockRequest;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.BlockResponse;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.DayAvailability;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.DaySummary;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.ScheduleRequest;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.ScheduleResponse;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.SlotState;
import com.ngolahealth.backend.modules.scheduling.domain.Appointment;
import com.ngolahealth.backend.modules.scheduling.domain.AppointmentRepository;
import com.ngolahealth.backend.modules.scheduling.domain.AppointmentStatus;
import com.ngolahealth.backend.modules.scheduling.domain.DoctorSchedule;
import com.ngolahealth.backend.modules.scheduling.domain.DoctorScheduleRepository;
import com.ngolahealth.backend.modules.scheduling.domain.ScheduleBlock;
import com.ngolahealth.backend.modules.scheduling.domain.ScheduleBlockRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Horários semanais, ausências e cálculo de vagas. */
@Service
@RequiredArgsConstructor
public class ScheduleService {

  private static final List<AppointmentStatus> PENDING =
      List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);

  private final DoctorScheduleRepository schedules;
  private final ScheduleBlockRepository blocks;
  private final AppointmentRepository appointments;
  private final SchedulingSupport support;
  private final SchedulingProperties properties;
  private final AuditTrail audit;

  // ---------- Horários ----------

  @Transactional(readOnly = true)
  public List<ScheduleResponse> schedules(UUID doctorId, boolean includeInactive) {
    support.hospitalUser();
    var list = schedules.search(doctorId, includeInactive);
    var names = support.names(list.stream().map(DoctorSchedule::getDoctorId).toList());
    return list.stream().map(s -> toResponse(s, names, null)).toList();
  }

  @Transactional
  public ScheduleResponse createSchedule(ScheduleRequest r) {
    var user = support.hospitalUser();
    var doctorName = support.requireDoctor(r.doctorId(), user);
    if (!r.startTime().isBefore(r.endTime())) {
      throw new BadRequestException(
          "INVALID_TIME_RANGE", "A hora de início tem de ser anterior à hora de fim.");
    }
    if (r.startTime().plusMinutes(r.slotMinutes()).isAfter(r.endTime())) {
      throw new BadRequestException(
          "SLOT_LONGER_THAN_SCHEDULE", "A duração da vaga é maior do que o horário.");
    }
    // Bloqueia os horários existentes nesse dia: dois pedidos simultâneos não criam sobreposições.
    var sameDay = schedules.lockFor(r.doctorId(), r.dayOfWeek().getValue());
    sameDay.stream()
        .filter(s -> s.overlaps(r.startTime(), r.endTime()))
        .findFirst()
        .ifPresent(
            s -> {
              throw new ConflictException(
                  "SCHEDULE_OVERLAP",
                  "Sobrepõe-se ao horário das "
                      + s.getStartTime()
                      + " às "
                      + s.getEndTime()
                      + " do mesmo médico.");
            });
    var s =
        schedules.saveAndFlush(
            new DoctorSchedule(
                r.doctorId(),
                r.dayOfWeek(),
                r.startTime(),
                r.endTime(),
                r.slotMinutes(),
                r.capacity(),
                user.id()));
    var response = toResponse(s, Map.of(s.getDoctorId(), doctorName), null);
    audit.record(
        entry(
                AuditAction.CREATE,
                AuditEntityType.SCHEDULE,
                s.getId(),
                "Horário criado: " + label(response))
            .withChanges(audit.snapshot(response)));
    return response;
  }

  /**
   * Desactiva o horário. As marcações futuras mantêm-se (o paciente foi avisado de uma data): a
   * resposta indica quantas são, para a recepção as rever.
   */
  @Transactional
  public ScheduleResponse deactivateSchedule(UUID id) {
    support.hospitalUser();
    var s =
        schedules
            .findScoped(id)
            .orElseThrow(
                () -> new NotFoundException("SCHEDULE_NOT_FOUND", "Horário não encontrado."));
    s.deactivate();
    schedules.saveAndFlush(s);
    var today = support.today();
    int future =
        (int)
            appointments
                .inPeriod(
                    s.getDoctorId(), today, today.plusDays(properties.maxDaysAhead()), PENDING)
                .stream()
                .filter(
                    a ->
                        a.getDate().getDayOfWeek() == s.day()
                            && s.overlaps(a.getStartTime(), a.getEndTime()))
                .count();
    var response = toResponse(s, support.names(List.of(s.getDoctorId())), future);
    audit.record(
        entry(
            AuditAction.DEACTIVATE,
            AuditEntityType.SCHEDULE,
            s.getId(),
            "Horário desactivado: "
                + label(response)
                + " ("
                + future
                + " marcação(ões) futura(s))"));
    return response;
  }

  // ---------- Ausências ----------

  @Transactional(readOnly = true)
  public List<BlockResponse> blocks(UUID doctorId, LocalDate from, LocalDate to) {
    support.hospitalUser();
    var start = from != null ? from : support.today();
    var end = to != null ? to : start.plusDays(90);
    requireRange(start, end, 366);
    return toBlockResponses(blocks.between(doctorId, start, end), false);
  }

  @Transactional
  public BlockResponse createBlock(BlockRequest r) {
    var user = support.hospitalUser();
    support.requireOwnAgendaOrManager(r.doctorId(), user);
    support.requireDoctor(r.doctorId(), user);
    if ((r.startTime() == null) != (r.endTime() == null)
        || (r.startTime() != null && !r.startTime().isBefore(r.endTime()))) {
      throw new BadRequestException(
          "INVALID_BLOCK_TIMES",
          "Indique as duas horas (início antes do fim) ou nenhuma, para o dia inteiro.");
    }
    if (r.date().isBefore(support.today())) {
      throw new BadRequestException("BLOCK_IN_PAST", "Não se registam ausências no passado.");
    }
    var b =
        blocks.saveAndFlush(
            new ScheduleBlock(
                r.doctorId(),
                r.date(),
                r.startTime(),
                r.endTime(),
                r.reason().strip(),
                user.id(),
                support.instant()));
    var response = toBlockResponses(List.of(b), true).getFirst();
    audit.record(
        entry(
            AuditAction.CREATE,
            AuditEntityType.SCHEDULE_BLOCK,
            b.getId(),
            "Ausência registada: "
                + blockLabel(response)
                + " ("
                + response.affectedAppointments().size()
                + " marcação(ões) afectada(s))"));
    return response;
  }

  @Transactional
  public void deleteBlock(UUID id) {
    var user = support.hospitalUser();
    var b =
        blocks
            .findScoped(id)
            .orElseThrow(
                () -> new NotFoundException("BLOCK_NOT_FOUND", "Ausência não encontrada."));
    support.requireOwnAgendaOrManager(b.getDoctorId(), user);
    var response = toBlockResponses(List.of(b), false).getFirst();
    blocks.delete(b);
    audit.record(
        entry(
            AuditAction.DELETE,
            AuditEntityType.SCHEDULE_BLOCK,
            b.getId(),
            "Ausência removida: " + blockLabel(response)));
  }

  // ---------- Vagas ----------

  @Transactional(readOnly = true)
  public DayAvailability availability(UUID doctorId, LocalDate date) {
    var user = support.hospitalUser();
    var doctorName = support.requireDoctor(doctorId, user);
    var daySchedules = schedules.activeFor(doctorId, date.getDayOfWeek().getValue());
    var dayBlocks = blocks.between(doctorId, date, date);
    var dayAppointments =
        appointments.inPeriod(doctorId, date, date, AppointmentRepository.OCCUPYING);
    var slots = support.slots(date, daySchedules, dayBlocks, dayAppointments, support.now());
    return new DayAvailability(
        date,
        doctorId,
        doctorName,
        slots,
        (int) slots.stream().filter(s -> s.state() == SlotState.FREE).count(),
        toBlockResponses(dayBlocks, false));
  }

  /** Resumo de cada dia do mês: de um médico ou, sem médico, de todos. */
  @Transactional(readOnly = true)
  public List<DaySummary> month(UUID doctorId, YearMonth month) {
    support.hospitalUser();
    var from = month.atDay(1);
    var to = month.atEndOfMonth();
    var allSchedules = schedules.search(doctorId, false);
    var monthBlocks = blocks.between(doctorId, from, to);
    var monthAppointments =
        appointments.inPeriod(doctorId, from, to, AppointmentRepository.OCCUPYING);
    var doctors =
        Stream.concat(
                allSchedules.stream().map(DoctorSchedule::getDoctorId),
                monthAppointments.stream().map(Appointment::getDoctorId))
            .collect(Collectors.toSet());
    var now = support.now();
    var days = new ArrayList<DaySummary>();
    for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
      final var d = date;
      int total = 0;
      int free = 0;
      boolean anyWorking = false;
      boolean allBlocked = true;
      for (var doctor : doctors) {
        var daySchedules =
            allSchedules.stream()
                .filter(s -> s.getDoctorId().equals(doctor) && s.day() == d.getDayOfWeek())
                .toList();
        if (daySchedules.isEmpty()) {
          continue;
        }
        anyWorking = true;
        var dayBlocks =
            monthBlocks.stream()
                .filter(b -> b.getDoctorId().equals(doctor) && b.getBlockDate().equals(d))
                .toList();
        allBlocked &= dayBlocks.stream().anyMatch(ScheduleBlock::isAllDay);
        var dayAppointments =
            monthAppointments.stream()
                .filter(a -> a.getDoctorId().equals(doctor) && a.getDate().equals(d))
                .toList();
        var slots = support.slots(d, daySchedules, dayBlocks, dayAppointments, now);
        total += slots.size();
        free += (int) slots.stream().filter(s -> s.state() == SlotState.FREE).count();
      }
      int booked =
          (int)
              monthAppointments.stream()
                  .filter(a -> a.getDate().equals(d) && a.getStatus().isPending())
                  .count();
      days.add(new DaySummary(d, total, free, booked, anyWorking && allBlocked));
    }
    return days;
  }

  // ---------- Auxiliares ----------

  static void requireRange(LocalDate from, LocalDate to, int maxDays) {
    if (to.isBefore(from)) {
      throw new BadRequestException("INVALID_RANGE", "A data final é anterior à inicial.");
    }
    if (from.plusDays(maxDays).isBefore(to)) {
      throw new BadRequestException(
          "RANGE_TOO_LONG", "O intervalo não pode exceder " + maxDays + " dias.");
    }
  }

  private List<BlockResponse> toBlockResponses(List<ScheduleBlock> list, boolean withAffected) {
    var names =
        support.names(
            list.stream()
                .flatMap(b -> Stream.of(b.getDoctorId(), b.getCreatedBy()))
                .filter(Objects::nonNull)
                .toList());
    return list.stream()
        .map(
            b ->
                new BlockResponse(
                    b.getId(),
                    b.getDoctorId(),
                    names.get(b.getDoctorId()),
                    b.getBlockDate(),
                    b.getStartTime(),
                    b.getEndTime(),
                    b.isAllDay(),
                    b.getReason(),
                    names.get(b.getCreatedBy()),
                    withAffected
                        ? support.toResponses(
                            appointments
                                .inPeriod(
                                    b.getDoctorId(), b.getBlockDate(), b.getBlockDate(), PENDING)
                                .stream()
                                .filter(a -> b.covers(a.getStartTime(), a.getEndTime()))
                                .toList())
                        : List.of()))
        .toList();
  }

  private static ScheduleResponse toResponse(
      DoctorSchedule s, Map<UUID, String> names, Integer futureAppointments) {
    return new ScheduleResponse(
        s.getId(),
        s.getDoctorId(),
        names.get(s.getDoctorId()),
        s.day(),
        s.getStartTime(),
        s.getEndTime(),
        s.getSlotMinutes(),
        s.getCapacity(),
        s.slotStarts().size(),
        s.isActive(),
        futureAppointments);
  }

  private static String label(ScheduleResponse s) {
    return s.doctorName()
        + ", "
        + s.dayOfWeek()
        + " "
        + s.startTime()
        + "–"
        + s.endTime()
        + ", vagas de "
        + s.slotMinutes()
        + " min × "
        + s.capacity();
  }

  private static String blockLabel(BlockResponse b) {
    return b.doctorName()
        + ", "
        + b.date().format(SchedulingSupport.DATE)
        + (b.allDay() ? " (dia inteiro)" : " " + b.startTime() + "–" + b.endTime())
        + ": "
        + b.reason();
  }

  private AuditEntry entry(AuditAction action, AuditEntityType type, UUID id, String summary) {
    return AuditEntry.success(action, type, id, support.hospitalUser().hospitalId(), summary);
  }
}
