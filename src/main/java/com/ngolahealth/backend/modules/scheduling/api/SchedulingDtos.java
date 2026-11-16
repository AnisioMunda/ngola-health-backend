package com.ngolahealth.backend.modules.scheduling.api;

import com.ngolahealth.backend.modules.scheduling.domain.AppointmentStatus;
import com.ngolahealth.backend.modules.scheduling.domain.AppointmentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class SchedulingDtos {

  private SchedulingDtos() {}

  // ---------- Horários ----------

  public record ScheduleRequest(
      @NotNull(message = "Seleccione o médico.") UUID doctorId,
      @NotNull(message = "Seleccione o dia da semana.") DayOfWeek dayOfWeek,
      @NotNull(message = "Indique a hora de início.") LocalTime startTime,
      @NotNull(message = "Indique a hora de fim.") LocalTime endTime,
      @NotNull(message = "Indique a duração de cada vaga.")
          @Min(value = 5, message = "Cada vaga tem pelo menos 5 minutos.")
          @Max(value = 240, message = "Cada vaga tem no máximo 240 minutos.")
          Integer slotMinutes,
      @NotNull(message = "Indique quantos pacientes cabem em cada vaga.")
          @Min(value = 1, message = "Cada vaga recebe pelo menos 1 paciente.")
          @Max(value = 20, message = "Cada vaga recebe no máximo 20 pacientes.")
          Integer capacity) {}

  /**
   * @param futureAppointments só na desactivação: marcações futuras que continuam válidas e que a
   *     recepção deve rever
   */
  public record ScheduleResponse(
      UUID id,
      UUID doctorId,
      String doctorName,
      DayOfWeek dayOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      int slotMinutes,
      int capacity,
      int slotsPerDay,
      boolean active,
      Integer futureAppointments) {}

  // ---------- Bloqueios ----------

  public record BlockRequest(
      @NotNull(message = "Seleccione o médico.") UUID doctorId,
      @NotNull(message = "Indique a data.") LocalDate date,
      LocalTime startTime,
      LocalTime endTime,
      @NotBlank(message = "Indique o motivo (ex.: formação, férias).")
          @Size(max = 200, message = "O motivo não pode exceder 200 caracteres.")
          String reason) {}

  /**
   * @param affectedAppointments marcações por realizar no período bloqueado: não são canceladas
   *     automaticamente; a recepção deve remarcá-las
   */
  public record BlockResponse(
      UUID id,
      UUID doctorId,
      String doctorName,
      LocalDate date,
      LocalTime startTime,
      LocalTime endTime,
      boolean allDay,
      String reason,
      String createdByName,
      List<AppointmentResponse> affectedAppointments) {}

  // ---------- Disponibilidade ----------

  public enum SlotState {
    FREE,
    FULL,
    BLOCKED,
    PAST
  }

  public record Slot(
      LocalTime startTime, LocalTime endTime, int capacity, int booked, SlotState state) {}

  public record DayAvailability(
      LocalDate date,
      UUID doctorId,
      String doctorName,
      List<Slot> slots,
      int freeSlots,
      List<BlockResponse> blocks) {}

  /** Um dia no calendário mensal. */
  public record DaySummary(
      LocalDate date, int totalSlots, int freeSlots, int appointments, boolean blockedAllDay) {}

  // ---------- Marcações ----------

  public record AppointmentRequest(
      @NotNull(message = "Seleccione o paciente.") UUID patientId,
      @NotNull(message = "Seleccione o médico.") UUID doctorId,
      @NotNull(message = "Seleccione o tipo de marcação.") AppointmentType type,
      @NotNull(message = "Indique a data.") LocalDate date,
      @NotNull(message = "Seleccione a vaga.") LocalTime startTime,
      @NotBlank(message = "Indique o motivo.")
          @Size(max = 300, message = "O motivo não pode exceder 300 caracteres.")
          String reason,
      @Size(max = 1000, message = "As observações não podem exceder 1000 caracteres.")
          String notes) {}

  public record RescheduleRequest(
      UUID doctorId,
      @NotNull(message = "Indique a nova data.") LocalDate date,
      @NotNull(message = "Seleccione a nova vaga.") LocalTime startTime,
      @Size(max = 200, message = "O motivo não pode exceder 200 caracteres.") String reason) {}

  public record CancelRequest(
      @NotBlank(message = "Indique o motivo do cancelamento.")
          @Size(max = 300, message = "O motivo não pode exceder 300 caracteres.")
          String reason) {}

  public record AppointmentResponse(
      UUID id,
      UUID patientId,
      String patientName,
      String patientRecordNumber,
      String patientPhone,
      UUID doctorId,
      String doctorName,
      AppointmentType type,
      LocalDate date,
      LocalTime startTime,
      LocalTime endTime,
      int slotPosition,
      AppointmentStatus status,
      String reason,
      String notes,
      String bookedByName,
      Instant bookedAt,
      Instant confirmedAt,
      Instant checkedInAt,
      UUID episodeId,
      Instant noShowAt,
      Instant cancelledAt,
      String cancelledByName,
      String cancellationReason,
      UUID rescheduledFromId,
      UUID rescheduledToId,
      long version) {}
}
