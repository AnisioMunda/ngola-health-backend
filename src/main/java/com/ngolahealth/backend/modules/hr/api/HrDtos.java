package com.ngolahealth.backend.modules.hr.api;

import com.ngolahealth.backend.modules.hr.domain.LeaveStatus;
import com.ngolahealth.backend.modules.hr.domain.LeaveType;
import com.ngolahealth.backend.modules.hr.domain.ShiftStatus;
import com.ngolahealth.backend.modules.hr.domain.ShiftType;
import com.ngolahealth.backend.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class HrDtos {

  private HrDtos() {}

  public record StaffResponse(UUID id, String fullName, Set<Role> roles) {}

  // ---------- Turnos ----------

  /** Data e horas locais; se o fim for antes do início, o turno acaba no dia seguinte. */
  public record ShiftRequest(
      @NotNull(message = "Seleccione o profissional.") UUID userId,
      @NotNull(message = "Seleccione o tipo de turno.") ShiftType type,
      @NotNull(message = "Indique a data.") LocalDate date,
      @NotNull(message = "Indique a hora de início.") LocalTime startTime,
      @NotNull(message = "Indique a hora de fim.") LocalTime endTime,
      UUID wardId,
      @Size(max = 100, message = "O serviço não pode exceder 100 caracteres.") String department,
      @Size(max = 300, message = "As notas não podem exceder 300 caracteres.") String notes) {}

  public record ShiftResponse(
      UUID id,
      UUID userId,
      String userName,
      ShiftType type,
      Instant startsAt,
      Instant endsAt,
      LocalDate date,
      UUID wardId,
      String wardName,
      String department,
      ShiftStatus status,
      String notes,
      String cancellationReason) {}

  public record CopyWeekRequest(
      @NotNull(message = "Indique a semana de origem.") LocalDate fromWeek,
      @NotNull(message = "Indique a semana de destino.") LocalDate toWeek) {}

  public record CopyWeekResponse(int created, List<String> skipped) {}

  public record ReasonRequest(
      @NotBlank(message = "Indique o motivo.")
          @Size(max = 300, message = "O motivo não pode exceder 300 caracteres.")
          String reason) {}

  // ---------- Ausências ----------

  public record LeaveRequestBody(
      @NotNull(message = "Seleccione o tipo de ausência.") LeaveType type,
      @NotNull(message = "Indique a data de início.") LocalDate startDate,
      @NotNull(message = "Indique a data de fim.") LocalDate endDate,
      @Size(max = 500, message = "O motivo não pode exceder 500 caracteres.") String reason) {}

  public record DecisionRequest(
      @NotNull(message = "Indique se aprova ou recusa.") Boolean approve,
      @Size(max = 300, message = "A nota não pode exceder 300 caracteres.") String note,
      Boolean cancelConflictingShifts) {}

  public record LeaveResponse(
      UUID id,
      UUID userId,
      String userName,
      LeaveType type,
      LocalDate startDate,
      LocalDate endDate,
      int days,
      String reason,
      LeaveStatus status,
      String decidedByName,
      Instant decidedAt,
      String decisionNote,
      Instant createdAt) {}

  // ---------- Ponto ----------

  public record AttendanceResponse(
      UUID id,
      UUID userId,
      String userName,
      UUID shiftId,
      Instant shiftStartsAt,
      Instant shiftEndsAt,
      Instant clockIn,
      Instant clockOut,
      Integer minutesWorked,
      int lateMinutes,
      int overtimeMinutes,
      String correctedByName,
      String correctionReason) {}

  public record CorrectionRequest(
      @NotNull(message = "Indique a entrada.") Instant clockIn,
      Instant clockOut,
      @NotBlank(message = "Indique o motivo da correcção.")
          @Size(max = 300, message = "O motivo não pode exceder 300 caracteres.")
          String reason) {}

  /** Estado de um turno num dia, para o relatório de assiduidade. */
  public enum PresenceState {
    PRESENT,
    LATE,
    ON_DUTY,
    UPCOMING,
    ABSENT,
    ON_LEAVE
  }

  public record DailyPresence(
      ShiftResponse shift, PresenceState state, AttendanceResponse attendance) {}

  public record Overview(
      int onDutyNow,
      int shiftsToday,
      int absentToday,
      int lateToday,
      long pendingLeaves,
      int onLeaveToday) {}
}
