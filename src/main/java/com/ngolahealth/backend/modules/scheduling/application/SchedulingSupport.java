package com.ngolahealth.backend.modules.scheduling.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.AppointmentResponse;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.Slot;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.SlotState;
import com.ngolahealth.backend.modules.scheduling.domain.Appointment;
import com.ngolahealth.backend.modules.scheduling.domain.DoctorSchedule;
import com.ngolahealth.backend.modules.scheduling.domain.ScheduleBlock;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.CurrentUserProvider;
import com.ngolahealth.backend.security.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Regras e conversões partilhadas pelos serviços de agendamento. */
@Component
@RequiredArgsConstructor
class SchedulingSupport {

  static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final CurrentUserProvider currentUser;
  private final StaffDirectory staff;
  private final PatientDirectory patients;
  private final AppProperties app;
  private final Clock clock;

  CurrentUser hospitalUser() {
    var user = currentUser.get();
    if (user.hospitalId() == null) {
      throw new ForbiddenException(
          "HOSPITAL_SCOPE_REQUIRED",
          "O agendamento só está acessível a utilizadores de um hospital.");
    }
    return user;
  }

  LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), app.timeZone());
  }

  LocalDate today() {
    return now().toLocalDate();
  }

  java.time.Instant instant() {
    return clock.instant();
  }

  java.time.Instant instantOf(LocalDate date, LocalTime time) {
    return LocalDateTime.of(date, time).atZone(app.timeZone()).toInstant();
  }

  String requireDoctor(UUID doctorId, CurrentUser user) {
    return staff
        .findActive(doctorId, user.hospitalId(), Role.DOCTOR)
        .map(StaffDirectory.StaffRef::fullName)
        .orElseThrow(
            () ->
                new BadRequestException(
                    "INVALID_DOCTOR",
                    "O médico indicado não existe, está inactivo ou é de outro hospital."));
  }

  /** O médico só gere a própria agenda; administração e direcção gerem a de todos. */
  void requireOwnAgendaOrManager(UUID doctorId, CurrentUser user) {
    boolean manager = user.hasRole(Role.ADMIN) || user.hasRole(Role.MANAGER);
    if (!manager && !doctorId.equals(user.id())) {
      throw new ForbiddenException(
          "OWN_AGENDA_ONLY", "Só pode registar ausências na sua própria agenda.");
    }
  }

  Map<UUID, String> names(Collection<UUID> ids) {
    return staff.names(ids);
  }

  /** Vagas de um médico num dia, com o estado de cada uma. */
  List<Slot> slots(
      LocalDate date,
      List<DoctorSchedule> daySchedules,
      List<ScheduleBlock> dayBlocks,
      List<Appointment> dayAppointments,
      LocalDateTime now) {
    var result = new ArrayList<Slot>();
    for (var s : daySchedules) {
      for (var start : s.slotStarts()) {
        var end = start.plusMinutes(s.getSlotMinutes());
        int booked =
            (int) dayAppointments.stream().filter(a -> a.getStartTime().equals(start)).count();
        SlotState state;
        if (!LocalDateTime.of(date, start).isAfter(now)) {
          state = SlotState.PAST;
        } else if (dayBlocks.stream().anyMatch(b -> b.covers(start, end))) {
          state = SlotState.BLOCKED;
        } else if (booked >= s.getCapacity()) {
          state = SlotState.FULL;
        } else {
          state = SlotState.FREE;
        }
        result.add(new Slot(start, end, s.getCapacity(), booked, state));
      }
    }
    result.sort(Comparator.comparing(Slot::startTime));
    return result;
  }

  List<AppointmentResponse> toResponses(List<Appointment> list) {
    var patientRefs = patients.findAll(list.stream().map(Appointment::getPatientId).toList());
    var people = new HashSet<UUID>();
    list.forEach(
        a ->
            Stream.of(a.getDoctorId(), a.getBookedBy(), a.getCancelledBy())
                .filter(java.util.Objects::nonNull)
                .forEach(people::add));
    var names = staff.names(people);
    return list.stream()
        .map(
            a -> {
              var p = patientRefs.get(a.getPatientId());
              return new AppointmentResponse(
                  a.getId(),
                  a.getPatientId(),
                  p == null ? null : p.fullName(),
                  p == null ? null : p.recordNumber(),
                  p == null ? null : p.phone(),
                  a.getDoctorId(),
                  names.get(a.getDoctorId()),
                  a.getType(),
                  a.getDate(),
                  a.getStartTime(),
                  a.getEndTime(),
                  a.getSlotPosition(),
                  a.getStatus(),
                  a.getReason(),
                  a.getNotes(),
                  names.get(a.getBookedBy()),
                  a.getBookedAt(),
                  a.getConfirmedAt(),
                  a.getCheckedInAt(),
                  a.getEpisodeId(),
                  a.getNoShowAt(),
                  a.getCancelledAt(),
                  names.get(a.getCancelledBy()),
                  a.getCancellationReason(),
                  a.getRescheduledFromId(),
                  a.getRescheduledToId(),
                  a.getVersion() == null ? 0 : a.getVersion());
            })
        .toList();
  }

  AppointmentResponse toResponse(Appointment a) {
    return toResponses(List.of(a)).getFirst();
  }

  static String describe(AppointmentResponse a) {
    return a.patientName()
        + " (processo "
        + a.patientRecordNumber()
        + ") com "
        + a.doctorName()
        + " em "
        + a.date().format(DATE)
        + " às "
        + a.startTime();
  }
}
