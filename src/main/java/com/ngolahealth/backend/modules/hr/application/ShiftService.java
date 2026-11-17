package com.ngolahealth.backend.modules.hr.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.hr.api.HrDtos.CopyWeekResponse;
import com.ngolahealth.backend.modules.hr.api.HrDtos.ShiftRequest;
import com.ngolahealth.backend.modules.hr.api.HrDtos.ShiftResponse;
import com.ngolahealth.backend.modules.hr.api.HrDtos.StaffResponse;
import com.ngolahealth.backend.modules.hr.domain.LeaveRequestRepository;
import com.ngolahealth.backend.modules.hr.domain.Shift;
import com.ngolahealth.backend.modules.hr.domain.ShiftRepository;
import com.ngolahealth.backend.security.CurrentUser;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Escala: turnos dos profissionais. */
@Service
@RequiredArgsConstructor
public class ShiftService {

  static final Duration MAX_SHIFT = Duration.ofHours(24);
  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm");

  private final ShiftRepository shifts;
  private final LeaveRequestRepository leaves;
  private final HrSupport support;
  private final AuditTrail audit;

  @Transactional(readOnly = true)
  public List<StaffResponse> staff() {
    return support.staff(support.hospitalUser()).stream()
        .map(s -> new StaffResponse(s.id(), s.fullName(), s.roles()))
        .toList();
  }

  /** Turnos que começam entre {@code from} e {@code to} (datas locais, inclusive). */
  @Transactional(readOnly = true)
  public List<ShiftResponse> search(
      LocalDate from, LocalDate to, UUID userId, boolean includeCancelled) {
    support.hospitalUser();
    if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 62) {
      throw new BadRequestException("INVALID_RANGE", "Indique um período válido (máximo 62 dias).");
    }
    return support.toShifts(
        shifts.between(
            support.startOf(from), support.startOf(to.plusDays(1)), userId, includeCancelled));
  }

  @Transactional(readOnly = true)
  public List<ShiftResponse> mine(LocalDate from, LocalDate to) {
    var user = support.hospitalUser();
    return search(from, to, user.id(), false);
  }

  @Transactional
  public ShiftResponse create(ShiftRequest r) {
    var user = support.hospitalUser();
    var start = support.at(r.date(), r.startTime());
    var end =
        support.at(
            r.endTime().isAfter(r.startTime()) ? r.date() : r.date().plusDays(1), r.endTime());
    var created =
        schedule(
                r.userId(),
                r.type(),
                start,
                end,
                r.wardId(),
                HrSupport.optional(r.department()),
                HrSupport.optional(r.notes()),
                user)
            .orElseThrow();
    return support.toShifts(List.of(created)).getFirst();
  }

  @Transactional
  public ShiftResponse cancel(UUID id, String reason) {
    var user = support.hospitalUser();
    var s =
        shifts
            .findScoped(id)
            .orElseThrow(() -> new NotFoundException("SHIFT_NOT_FOUND", "Turno não encontrado."));
    s.cancel(reason.strip(), user.id(), support.now());
    shifts.saveAndFlush(s);
    var response = support.toShifts(List.of(s)).getFirst();
    audit.record(
        entry(
            AuditAction.CANCEL,
            s,
            user,
            "Turno cancelado (" + reason.strip() + "): " + describe(response)));
    return response;
  }

  /**
   * Copia os turnos de uma semana (segunda a domingo) para outra. Os que chocam com outro turno,
   * com uma ausência aprovada ou que ficariam no passado são saltados e listados.
   */
  @Transactional
  public CopyWeekResponse copyWeek(LocalDate fromWeek, LocalDate toWeek) {
    var user = support.hospitalUser();
    var from = fromWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    var to = toWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    if (from.equals(to)) {
      throw new BadRequestException("SAME_WEEK", "Escolha uma semana de destino diferente.");
    }
    long days = ChronoUnit.DAYS.between(from, to);
    var source =
        shifts.between(support.startOf(from), support.startOf(from.plusDays(7)), null, false);
    var names = support.names(source.stream().map(Shift::getUserId).toList());
    int created = 0;
    var skipped = new ArrayList<String>();
    for (var s : source) {
      var start = s.getStartsAt().plus(Duration.ofDays(days));
      var end = s.getEndsAt().plus(Duration.ofDays(days));
      var label =
          names.get(s.getUserId()) + " — " + WHEN.withZone(support.zone()).format(start) + ": ";
      try {
        var reason = conflict(s.getUserId(), start, end);
        if (reason != null) {
          skipped.add(label + reason);
          continue;
        }
        schedule(
            s.getUserId(),
            s.getType(),
            start,
            end,
            s.getWardId(),
            s.getDepartment(),
            s.getNotes(),
            user);
        created++;
      } catch (BadRequestException | ConflictException e) {
        skipped.add(label + e.getMessage());
      }
    }
    audit.record(
        AuditEntry.success(
            AuditAction.CREATE,
            AuditEntityType.SHIFT,
            null,
            user.hospitalId(),
            "Escala copiada da semana de "
                + from
                + " para "
                + to
                + ": "
                + created
                + " turno(s), "
                + skipped.size()
                + " saltado(s)"));
    return new CopyWeekResponse(created, skipped);
  }

  // ---------- Regras ----------

  private Optional<Shift> schedule(
      UUID userId,
      com.ngolahealth.backend.modules.hr.domain.ShiftType type,
      Instant start,
      Instant end,
      UUID wardId,
      String department,
      String notes,
      CurrentUser user) {
    var name = support.requireStaff(userId, user);
    if (Duration.between(start, end).compareTo(MAX_SHIFT) > 0) {
      throw new BadRequestException("SHIFT_TOO_LONG", "Um turno não pode exceder 24 horas.");
    }
    if (start.isBefore(support.now().minus(Duration.ofHours(24)))) {
      throw new BadRequestException("SHIFT_IN_PAST", "Não se marcam turnos no passado.");
    }
    if (wardId != null) {
      support.wardName(wardId);
    }
    var reason = conflict(userId, start, end);
    if (reason != null) {
      throw new ConflictException(
          reason.startsWith("ausência") ? "USER_ON_LEAVE" : "SHIFT_OVERLAP",
          name + ": " + reason + ".");
    }
    var s =
        shifts.saveAndFlush(
            new Shift(userId, type, start, end, wardId, department, notes, user.id()));
    audit.record(
        entry(
            AuditAction.CREATE,
            s,
            user,
            "Turno marcado: " + describe(support.toShifts(List.of(s)).getFirst())));
    return Optional.of(s);
  }

  /** Motivo pelo qual o turno não pode ser marcado, ou {@code null}. Leituras bloqueantes. */
  private String conflict(UUID userId, Instant start, Instant end) {
    var overlapping = shifts.lockActiveOverlapping(userId, start, end);
    if (!overlapping.isEmpty()) {
      var o = overlapping.getFirst();
      return "já tem turno de "
          + WHEN.withZone(support.zone()).format(o.getStartsAt())
          + " a "
          + WHEN.withZone(support.zone()).format(o.getEndsAt());
    }
    var onLeave =
        leaves
            .lockActiveOverlapping(userId, support.localDate(start), support.localDate(end))
            .stream()
            .filter(
                l ->
                    l.getStatus() == com.ngolahealth.backend.modules.hr.domain.LeaveStatus.APPROVED)
            .findFirst();
    return onLeave
        .map(l -> "ausência aprovada de " + l.getStartDate() + " a " + l.getEndDate())
        .orElse(null);
  }

  private static String describe(ShiftResponse s) {
    return s.userName() + ", " + s.type() + " " + s.startsAt() + " → " + s.endsAt();
  }

  private static AuditEntry entry(AuditAction action, Shift s, CurrentUser user, String summary) {
    return AuditEntry.success(action, AuditEntityType.SHIFT, s.getId(), user.hospitalId(), summary);
  }
}
