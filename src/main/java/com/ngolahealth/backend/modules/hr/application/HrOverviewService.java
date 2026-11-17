package com.ngolahealth.backend.modules.hr.application;

import com.ngolahealth.backend.modules.hr.api.HrDtos.DailyPresence;
import com.ngolahealth.backend.modules.hr.api.HrDtos.Overview;
import com.ngolahealth.backend.modules.hr.api.HrDtos.PresenceState;
import com.ngolahealth.backend.modules.hr.domain.AttendanceRecord;
import com.ngolahealth.backend.modules.hr.domain.AttendanceRepository;
import com.ngolahealth.backend.modules.hr.domain.LeaveRequest;
import com.ngolahealth.backend.modules.hr.domain.LeaveRequestRepository;
import com.ngolahealth.backend.modules.hr.domain.ShiftRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assiduidade do dia e painel de RH. */
@Service
@RequiredArgsConstructor
public class HrOverviewService {

  /** Atrasos até 5 minutos não contam. */
  static final int LATE_TOLERANCE_MINUTES = 5;

  private final ShiftRepository shifts;
  private final AttendanceRepository records;
  private final LeaveRequestRepository leaves;
  private final AttendanceService attendance;
  private final HrSupport support;

  /** Cada turno que começa no dia, com o estado de presença. */
  @Transactional(readOnly = true)
  public List<DailyPresence> daily(LocalDate requested) {
    support.hospitalUser();
    var date = requested != null ? requested : support.today();
    var dayShifts =
        shifts
            .between(support.startOf(date), support.startOf(date.plusDays(1)), null, false)
            .stream()
            .filter(s -> support.localDate(s.getStartsAt()).equals(date))
            .toList();
    var shiftResponses = support.toShifts(dayShifts);
    var byShift =
        dayShifts.isEmpty()
            ? java.util.Collections.<java.util.UUID, AttendanceRecord>emptyMap()
            : records.forShifts(dayShifts.stream().map(s -> s.getId()).toList()).stream()
                .collect(
                    Collectors.toMap(
                        AttendanceRecord::getShiftId, Function.identity(), (a, b) -> a));
    var onLeave =
        leaves.approvedBetween(null, date, date).stream()
            .map(LeaveRequest::getUserId)
            .collect(Collectors.toSet());
    var attendanceResponses =
        attendance.toResponses(new ArrayList<>(byShift.values())).stream()
            .collect(Collectors.toMap(a -> a.shiftId(), Function.identity()));
    var now = support.now();
    var result = new ArrayList<DailyPresence>();
    for (int i = 0; i < dayShifts.size(); i++) {
      var s = dayShifts.get(i);
      var a = byShift.get(s.getId());
      PresenceState state;
      if (onLeave.contains(s.getUserId())) {
        state = PresenceState.ON_LEAVE;
      } else if (a != null) {
        state =
            a.getLateMinutes() > LATE_TOLERANCE_MINUTES
                ? PresenceState.LATE
                : a.isOpen() ? PresenceState.ON_DUTY : PresenceState.PRESENT;
      } else if (now.isBefore(s.getStartsAt())) {
        state = PresenceState.UPCOMING;
      } else {
        state = PresenceState.ABSENT;
      }
      result.add(
          new DailyPresence(
              shiftResponses.get(i), state, a == null ? null : attendanceResponses.get(s.getId())));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public Overview overview() {
    support.hospitalUser();
    var today = support.today();
    var now = support.now();
    var presence = daily(today);
    int onDuty =
        (int)
            shifts.between(now, now.plusSeconds(1), null, false).stream()
                .map(s -> s.getUserId())
                .distinct()
                .count();
    var onLeave =
        new HashSet<>(
            leaves.approvedBetween(null, today, today).stream()
                .map(LeaveRequest::getUserId)
                .toList());
    return new Overview(
        onDuty,
        presence.size(),
        (int) presence.stream().filter(p -> p.state() == PresenceState.ABSENT).count(),
        (int) presence.stream().filter(p -> p.state() == PresenceState.LATE).count(),
        leaves.countPending(),
        onLeave.size());
  }
}
