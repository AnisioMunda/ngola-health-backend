package com.ngolahealth.backend.modules.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SlotRulesTest {

  private static LocalTime t(String s) {
    return LocalTime.parse(s);
  }

  @Test
  void slotsIgnoreTheShortRemainderAtTheEnd() {
    var s =
        new DoctorSchedule(
            UUID.randomUUID(), DayOfWeek.MONDAY, t("08:00"), t("09:10"), 20, 1, null);
    assertThat(s.slotStarts()).containsExactly(t("08:00"), t("08:20"), t("08:40"));
    assertThat(s.isSlotStart(t("08:40"))).isTrue();
    assertThat(s.isSlotStart(t("09:00"))).isFalse();
    assertThat(s.isSlotStart(t("08:10"))).isFalse();
  }

  @Test
  void scheduleEndingAtMidnightDoesNotWrap() {
    var s =
        new DoctorSchedule(
            UUID.randomUUID(), DayOfWeek.FRIDAY, t("22:00"), t("23:59"), 60, 1, null);
    assertThat(s.slotStarts()).containsExactly(t("22:00"));
  }

  @Test
  void blocksCoverOverlappingIntervalsOrTheWholeDay() {
    var partial =
        new ScheduleBlock(
            UUID.randomUUID(), LocalDate.now(), t("10:00"), t("11:00"), "x", null, Instant.now());
    assertThat(partial.covers(t("09:30"), t("10:00"))).isFalse();
    assertThat(partial.covers(t("09:45"), t("10:15"))).isTrue();
    assertThat(partial.covers(t("11:00"), t("11:30"))).isFalse();
    var allDay =
        new ScheduleBlock(UUID.randomUUID(), LocalDate.now(), null, null, "x", null, Instant.now());
    assertThat(allDay.covers(t("07:00"), t("07:30"))).isTrue();
  }
}
