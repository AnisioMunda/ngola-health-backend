package com.ngolahealth.backend.modules.hr.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngolahealth.backend.common.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HrRulesTest {

  private static final Instant EIGHT = Instant.parse("2026-11-16T07:00:00Z"); // 08:00 em Luanda

  @Test
  void attendanceAgainstShift() {
    var shift =
        new Shift(
            UUID.randomUUID(),
            ShiftType.MORNING,
            EIGHT,
            EIGHT.plusSeconds(8 * 3600),
            null,
            null,
            null,
            UUID.randomUUID());
    var r = new AttendanceRecord(shift.getUserId(), shift, EIGHT.plusSeconds(12 * 60));
    assertThat(r.getLateMinutes()).isEqualTo(12);
    assertThat(r.isOpen()).isTrue();
    r.clockOut(shift, EIGHT.plusSeconds(8 * 3600 + 45 * 60));
    assertThat(r.getMinutesWorked()).isEqualTo(8 * 60 + 33);
    assertThat(r.getOvertimeMinutes()).isEqualTo(45);
    var early = new AttendanceRecord(shift.getUserId(), shift, EIGHT.minusSeconds(600));
    assertThat(early.getLateMinutes()).isZero();
  }

  @Test
  void leaveCountsCalendarDaysAndCannotBeSelfApproved() {
    var user = UUID.randomUUID();
    var l =
        new LeaveRequest(
            user, LeaveType.VACATION, LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 2), null);
    assertThat(l.getDays()).isEqualTo(6);
    assertThatThrownBy(() -> l.decide(true, null, user, Instant.now()))
        .isInstanceOf(BusinessException.class);
    l.decide(true, null, UUID.randomUUID(), Instant.now());
    assertThatThrownBy(() -> l.cancel(LocalDate.of(2026, 12, 28)))
        .isInstanceOf(BusinessException.class);
  }
}
