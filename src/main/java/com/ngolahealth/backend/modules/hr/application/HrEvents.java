package com.ngolahealth.backend.modules.hr.application;

import java.time.LocalDate;
import java.util.UUID;

/** Eventos de RH, entregues depois do commit. */
public final class HrEvents {

  private HrEvents() {}

  public record LeaveRequested(
      UUID hospitalId,
      UUID leaveId,
      UUID userId,
      String userName,
      String type,
      LocalDate start,
      LocalDate end) {}

  public record LeaveDecided(
      UUID hospitalId,
      UUID leaveId,
      UUID userId,
      boolean approved,
      LocalDate start,
      LocalDate end,
      String note) {}
}
