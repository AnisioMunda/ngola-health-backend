package com.ngolahealth.backend.modules.hr.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.hr.api.HrDtos.AttendanceResponse;
import com.ngolahealth.backend.modules.hr.api.HrDtos.CorrectionRequest;
import com.ngolahealth.backend.modules.hr.domain.AttendanceRecord;
import com.ngolahealth.backend.modules.hr.domain.AttendanceRepository;
import com.ngolahealth.backend.modules.hr.domain.Shift;
import com.ngolahealth.backend.modules.hr.domain.ShiftRepository;
import com.ngolahealth.backend.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Controlo de ponto: entrada, saída, consulta e correcção por um gestor. */
@Service
@RequiredArgsConstructor
public class AttendanceService {

  /** A entrada pode ser feita até 2 horas antes do turno e liga-se a ele. */
  static final Duration EARLY_CLOCK_IN = Duration.ofHours(2);

  private final AttendanceRepository records;
  private final ShiftRepository shifts;
  private final HrSupport support;
  private final AuditTrail audit;

  @Transactional(readOnly = true)
  public Optional<AttendanceResponse> current() {
    var user = support.hospitalUser();
    return records.findOpen(user.id()).map(r -> toResponses(List.of(r)).getFirst());
  }

  @Transactional
  public AttendanceResponse clockIn() {
    var user = support.hospitalUser();
    if (records.lockOpen(user.id()).isPresent()) {
      throw new ConflictException("ALREADY_CLOCKED_IN", "Já tem uma entrada registada sem saída.");
    }
    var now = support.now();
    var shift = shiftAt(user.id(), now);
    AttendanceRecord r;
    try {
      r = records.saveAndFlush(new AttendanceRecord(user.id(), shift.orElse(null), now));
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("ALREADY_CLOCKED_IN", "Já tem uma entrada registada sem saída.");
    }
    var response = toResponses(List.of(r)).getFirst();
    audit.record(
        entry(
            AuditAction.CLOCK_IN,
            r,
            user,
            "Entrada"
                + (shift.isPresent()
                    ? " (turno"
                        + (r.getLateMinutes() > 0
                            ? ", " + r.getLateMinutes() + " min de atraso"
                            : "")
                        + ")"
                    : " (sem turno)")));
    return response;
  }

  @Transactional
  public AttendanceResponse clockOut() {
    var user = support.hospitalUser();
    var r =
        records
            .lockOpen(user.id())
            .orElseThrow(
                () -> new ConflictException("NOT_CLOCKED_IN", "Não tem entrada registada."));
    var shift = r.getShiftId() == null ? null : shifts.findScoped(r.getShiftId()).orElse(null);
    r.clockOut(shift, support.now());
    records.saveAndFlush(r);
    var response = toResponses(List.of(r)).getFirst();
    audit.record(
        entry(
            AuditAction.CLOCK_OUT,
            r,
            user,
            "Saída: "
                + response.minutesWorked()
                + " min trabalhados"
                + (r.getOvertimeMinutes() > 0
                    ? ", " + r.getOvertimeMinutes() + " min extra"
                    : "")));
    return response;
  }

  @Transactional(readOnly = true)
  public List<AttendanceResponse> search(LocalDate from, LocalDate to, UUID userId) {
    support.hospitalUser();
    if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 62) {
      throw new BadRequestException("INVALID_RANGE", "Indique um período válido (máximo 62 dias).");
    }
    return toResponses(
        records.between(support.startOf(from), support.startOf(to.plusDays(1)), userId));
  }

  @Transactional(readOnly = true)
  public List<AttendanceResponse> mine(LocalDate from, LocalDate to) {
    return search(from, to, support.hospitalUser().id());
  }

  /** Correcção por um gestor (nunca no próprio registo). */
  @Transactional
  public AttendanceResponse correct(UUID id, CorrectionRequest c) {
    var user = support.hospitalUser();
    var r =
        records
            .findForUpdate(id)
            .orElseThrow(
                () -> new NotFoundException("ATTENDANCE_NOT_FOUND", "Registo não encontrado."));
    if (r.getUserId().equals(user.id())) {
      throw new ForbiddenException("OWN_RECORD", "Não pode corrigir o seu próprio ponto.");
    }
    if (c.clockIn().isAfter(support.now())
        || (c.clockOut() != null && c.clockOut().isAfter(support.now()))) {
      throw new BadRequestException("FUTURE_TIME", "As horas não podem ser no futuro.");
    }
    var before = toResponses(List.of(r)).getFirst();
    var shift =
        r.getShiftId() == null
            ? shiftAt(r.getUserId(), c.clockIn()).orElse(null)
            : shifts.findScoped(r.getShiftId()).orElse(null);
    r.correct(c.clockIn(), c.clockOut(), shift, c.reason().strip(), user.id(), support.now());
    records.saveAndFlush(r);
    var after = toResponses(List.of(r)).getFirst();
    audit.record(
        entry(
                AuditAction.UPDATE,
                r,
                user,
                "Ponto corrigido de " + after.userName() + " (" + c.reason().strip() + ")")
            .withChanges(audit.diff(before, after)));
    return after;
  }

  /** Turno activo do profissional em curso (ou a começar dentro de 2 horas) no instante dado. */
  Optional<Shift> shiftAt(UUID userId, Instant at) {
    return shifts
        .between(at.minus(Duration.ofHours(24)), at.plus(EARLY_CLOCK_IN), userId, false)
        .stream()
        .filter(
            s -> !at.isBefore(s.getStartsAt().minus(EARLY_CLOCK_IN)) && at.isBefore(s.getEndsAt()))
        .min(Comparator.comparing(s -> Duration.between(s.getStartsAt(), at).abs()));
  }

  List<AttendanceResponse> toResponses(List<AttendanceRecord> list) {
    var shiftIds =
        list.stream()
            .map(AttendanceRecord::getShiftId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, Shift> shiftMap =
        shiftIds.isEmpty()
            ? java.util.Collections.emptyMap()
            : shifts.findAllById(shiftIds).stream()
                .collect(Collectors.toMap(Shift::getId, Function.identity()));
    var names =
        support.names(
            list.stream()
                .flatMap(r -> Stream.of(r.getUserId(), r.getCorrectedBy()))
                .filter(Objects::nonNull)
                .toList());
    return list.stream()
        .map(
            r -> {
              var s = shiftMap.get(r.getShiftId());
              return new AttendanceResponse(
                  r.getId(),
                  r.getUserId(),
                  names.get(r.getUserId()),
                  r.getShiftId(),
                  s == null ? null : s.getStartsAt(),
                  s == null ? null : s.getEndsAt(),
                  r.getClockIn(),
                  r.getClockOut(),
                  r.getMinutesWorked(),
                  r.getLateMinutes(),
                  r.getOvertimeMinutes(),
                  names.get(r.getCorrectedBy()),
                  r.getCorrectionReason());
            })
        .toList();
  }

  private static AuditEntry entry(
      AuditAction action, AttendanceRecord r, CurrentUser user, String summary) {
    return AuditEntry.success(
        action, AuditEntityType.ATTENDANCE, r.getId(), user.hospitalId(), summary);
  }
}
