package com.ngolahealth.backend.modules.hr.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.hr.api.HrDtos.DecisionRequest;
import com.ngolahealth.backend.modules.hr.api.HrDtos.LeaveRequestBody;
import com.ngolahealth.backend.modules.hr.api.HrDtos.LeaveResponse;
import com.ngolahealth.backend.modules.hr.domain.LeaveRequest;
import com.ngolahealth.backend.modules.hr.domain.LeaveRequestRepository;
import com.ngolahealth.backend.modules.hr.domain.LeaveStatus;
import com.ngolahealth.backend.modules.hr.domain.LeaveType;
import com.ngolahealth.backend.modules.hr.domain.ShiftRepository;
import com.ngolahealth.backend.security.CurrentUser;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pedidos de ausência (folgas, férias, baixas) e a sua aprovação. */
@Service
@RequiredArgsConstructor
public class LeaveService {

  /** Uma baixa médica pode ser registada depois de começar (até 30 dias). */
  static final int SICK_BACKDATE_DAYS = 30;

  private final LeaveRequestRepository leaves;
  private final ShiftRepository shifts;
  private final HrSupport support;
  private final AuditTrail audit;
  private final ApplicationEventPublisher events;

  @Transactional
  public LeaveResponse request(LeaveRequestBody r) {
    var user = support.hospitalUser();
    var today = support.today();
    if (r.endDate().isBefore(r.startDate())) {
      throw new BadRequestException("INVALID_PERIOD", "A data de fim é anterior à de início.");
    }
    if (ChronoUnit.DAYS.between(r.startDate(), r.endDate()) >= 365) {
      throw new BadRequestException("PERIOD_TOO_LONG", "Uma ausência não pode exceder 365 dias.");
    }
    var earliest = r.type() == LeaveType.SICK ? today.minusDays(SICK_BACKDATE_DAYS) : today;
    if (r.startDate().isBefore(earliest)) {
      throw new BadRequestException(
          "LEAVE_IN_PAST",
          r.type() == LeaveType.SICK
              ? "Uma baixa só se regista até " + SICK_BACKDATE_DAYS + " dias depois de começar."
              : "Não se pedem ausências para o passado.");
    }
    if (!leaves.lockActiveOverlapping(user.id(), r.startDate(), r.endDate()).isEmpty()) {
      throw new ConflictException(
          "LEAVE_OVERLAP", "Já tem um pedido pendente ou aprovado que se sobrepõe a este período.");
    }
    var l =
        leaves.saveAndFlush(
            new LeaveRequest(
                user.id(), r.type(), r.startDate(), r.endDate(), HrSupport.optional(r.reason())));
    var response = toResponses(List.of(l)).getFirst();
    audit.record(entry(AuditAction.CREATE, l, user, "Ausência pedida: " + describe(response)));
    events.publishEvent(
        new HrEvents.LeaveRequested(
            user.hospitalId(),
            l.getId(),
            user.id(),
            user.fullName(),
            r.type().name(),
            r.startDate(),
            r.endDate()));
    return response;
  }

  @Transactional(readOnly = true)
  public PageResponse<LeaveResponse> mine(int page, int size) {
    var user = support.hospitalUser();
    return search(null, user.id(), null, null, page, size);
  }

  @Transactional(readOnly = true)
  public PageResponse<LeaveResponse> search(
      LeaveStatus status, UUID userId, LocalDate from, LocalDate to, int page, int size) {
    support.hospitalUser();
    var result =
        leaves.search(
            status == null ? EnumSet.allOf(LeaveStatus.class) : EnumSet.of(status),
            userId,
            from,
            to,
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("startDate"), Sort.Order.asc("id"))));
    var mapped = toResponses(result.getContent());
    var byId = new java.util.HashMap<UUID, LeaveResponse>();
    mapped.forEach(m -> byId.put(m.id(), m));
    return PageResponse.of(result, l -> byId.get(l.getId()));
  }

  /**
   * Aprovar com turnos marcados no período exige confirmação ({@code cancelConflictingShifts}): os
   * turnos são então cancelados, para a escala não mostrar quem não vai estar.
   */
  @Transactional
  public LeaveResponse decide(UUID id, DecisionRequest r) {
    var user = support.hospitalUser();
    var l = lock(id);
    if (r.approve()) {
      var conflicting =
          shifts.lockActiveOverlapping(
              l.getUserId(),
              support.startOf(l.getStartDate()),
              support.startOf(l.getEndDate().plusDays(1)));
      if (!conflicting.isEmpty()) {
        if (!Boolean.TRUE.equals(r.cancelConflictingShifts())) {
          throw new ConflictException(
              "LEAVE_SHIFT_CONFLICT",
              "O profissional tem "
                  + conflicting.size()
                  + " turno(s) marcado(s) neste período. Confirme para os cancelar.");
        }
        for (var s : conflicting) {
          s.cancel("Ausência aprovada", user.id(), support.now());
          shifts.save(s);
        }
      }
    }
    l.decide(r.approve(), HrSupport.optional(r.note()), user.id(), support.now());
    leaves.saveAndFlush(l);
    var response = toResponses(List.of(l)).getFirst();
    audit.record(
        entry(
            r.approve() ? AuditAction.APPROVE : AuditAction.REJECT,
            l,
            user,
            (r.approve() ? "Ausência aprovada: " : "Ausência recusada: ") + describe(response)));
    events.publishEvent(
        new HrEvents.LeaveDecided(
            user.hospitalId(),
            l.getId(),
            l.getUserId(),
            r.approve(),
            l.getStartDate(),
            l.getEndDate(),
            l.getDecisionNote()));
    return response;
  }

  /** O próprio cancela o seu pedido; um gestor também pode. */
  @Transactional
  public LeaveResponse cancel(UUID id) {
    var user = support.hospitalUser();
    var l = lock(id);
    if (!l.getUserId().equals(user.id()) && !HrSupport.isManager(user)) {
      throw new ForbiddenException("NOT_YOUR_LEAVE", "Só pode cancelar os seus pedidos.");
    }
    l.cancel(support.today());
    leaves.saveAndFlush(l);
    var response = toResponses(List.of(l)).getFirst();
    audit.record(entry(AuditAction.CANCEL, l, user, "Ausência cancelada: " + describe(response)));
    return response;
  }

  private LeaveRequest lock(UUID id) {
    return leaves
        .findForUpdate(id)
        .orElseThrow(() -> new NotFoundException("LEAVE_NOT_FOUND", "Pedido não encontrado."));
  }

  private List<LeaveResponse> toResponses(List<LeaveRequest> list) {
    var names =
        support.names(
            list.stream()
                .flatMap(l -> Stream.of(l.getUserId(), l.getDecidedBy()))
                .filter(Objects::nonNull)
                .toList());
    return list.stream()
        .map(
            l ->
                new LeaveResponse(
                    l.getId(),
                    l.getUserId(),
                    names.get(l.getUserId()),
                    l.getType(),
                    l.getStartDate(),
                    l.getEndDate(),
                    l.getDays(),
                    l.getReason(),
                    l.getStatus(),
                    names.get(l.getDecidedBy()),
                    l.getDecidedAt(),
                    l.getDecisionNote(),
                    l.getCreatedAt()))
        .toList();
  }

  private static String describe(LeaveResponse l) {
    return l.userName()
        + ", "
        + l.type()
        + " de "
        + l.startDate()
        + " a "
        + l.endDate()
        + " ("
        + l.days()
        + " dia(s))";
  }

  private static AuditEntry entry(
      AuditAction action, LeaveRequest l, CurrentUser user, String summary) {
    return AuditEntry.success(
        action, AuditEntityType.LEAVE_REQUEST, l.getId(), user.hospitalId(), summary);
  }
}
