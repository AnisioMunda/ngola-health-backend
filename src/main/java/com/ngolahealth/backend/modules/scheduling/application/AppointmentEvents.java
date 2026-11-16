package com.ngolahealth.backend.modules.scheduling.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Eventos do agendamento, entregues depois do commit (ex.: às notificações). */
public final class AppointmentEvents {

  private AppointmentEvents() {}

  /** Cancelada por alguém que não o próprio médico. */
  public record Cancelled(
      UUID hospitalId,
      UUID appointmentId,
      UUID doctorId,
      String patientLabel,
      LocalDate date,
      LocalTime startTime,
      String reason) {}

  /** Remarcada: avisa o médico antigo e, se mudou, o novo (nunca quem remarcou). */
  public record Rescheduled(
      UUID hospitalId,
      UUID actorId,
      UUID newAppointmentId,
      UUID previousDoctorId,
      UUID newDoctorId,
      String patientLabel,
      LocalDate previousDate,
      LocalTime previousStart,
      LocalDate newDate,
      LocalTime newStart) {}

  /** Agenda de um médico para um dia (lembrete da véspera). */
  public record AgendaReminder(
      UUID hospitalId, UUID doctorId, LocalDate date, int count, LocalTime first, LocalTime last) {}
}
