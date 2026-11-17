package com.ngolahealth.backend.modules.hr.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.modules.hr.api.HrDtos.ShiftResponse;
import com.ngolahealth.backend.modules.hr.domain.Shift;
import com.ngolahealth.backend.modules.inpatient.application.WardDirectory;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.CurrentUserProvider;
import com.ngolahealth.backend.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class HrSupport {

  private final CurrentUserProvider currentUser;
  private final StaffDirectory staff;
  private final WardDirectory wards;
  private final AppProperties app;
  private final Clock clock;

  CurrentUser hospitalUser() {
    var user = currentUser.get();
    if (user.hospitalId() == null) {
      throw new ForbiddenException(
          "HOSPITAL_SCOPE_REQUIRED",
          "Os recursos humanos só estão acessíveis a utilizadores de um hospital.");
    }
    return user;
  }

  static boolean isManager(CurrentUser user) {
    return user.hasRole(Role.ADMIN) || user.hasRole(Role.MANAGER);
  }

  ZoneId zone() {
    return app.timeZone();
  }

  Instant now() {
    return clock.instant();
  }

  LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), app.timeZone());
  }

  Instant at(LocalDate date, LocalTime time) {
    return date.atTime(time).atZone(app.timeZone()).toInstant();
  }

  Instant startOf(LocalDate date) {
    return date.atStartOfDay(app.timeZone()).toInstant();
  }

  LocalDate localDate(Instant instant) {
    return LocalDate.ofInstant(instant, app.timeZone());
  }

  /** Nome do profissional activo do hospital (não paciente). */
  String requireStaff(UUID userId, CurrentUser user) {
    return staff.listStaff(user.hospitalId()).stream()
        .filter(s -> s.id().equals(userId))
        .findFirst()
        .map(StaffDirectory.StaffMember::fullName)
        .orElseThrow(
            () ->
                new BadRequestException(
                    "INVALID_STAFF",
                    "O profissional não existe, está inactivo ou é de outro hospital."));
  }

  List<StaffDirectory.StaffMember> staff(CurrentUser user) {
    return staff.listStaff(user.hospitalId());
  }

  Map<UUID, String> names(Collection<UUID> ids) {
    return staff.names(ids);
  }

  String wardName(UUID wardId) {
    return wards
        .activeName(wardId)
        .orElseThrow(
            () ->
                new BadRequestException(
                    "WARD_NOT_FOUND", "A enfermaria não existe ou está desactivada."));
  }

  List<ShiftResponse> toShifts(List<Shift> list) {
    var names = staff.names(list.stream().map(Shift::getUserId).toList());
    var wardNames =
        wards.names(
            list.stream().map(Shift::getWardId).filter(Objects::nonNull).distinct().toList());
    return list.stream()
        .map(
            s ->
                new ShiftResponse(
                    s.getId(),
                    s.getUserId(),
                    names.get(s.getUserId()),
                    s.getType(),
                    s.getStartsAt(),
                    s.getEndsAt(),
                    localDate(s.getStartsAt()),
                    s.getWardId(),
                    s.getWardId() == null ? null : wardNames.get(s.getWardId()),
                    s.getDepartment(),
                    s.getStatus(),
                    s.getNotes(),
                    s.getCancellationReason()))
        .toList();
  }

  static String optional(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }
}
