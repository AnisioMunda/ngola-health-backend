package com.ngolahealth.backend.modules.notifications.application;

import com.ngolahealth.backend.modules.notifications.domain.NotificationContent;
import com.ngolahealth.backend.modules.notifications.domain.NotificationPriority;
import com.ngolahealth.backend.modules.notifications.domain.NotificationType;
import com.ngolahealth.backend.modules.scheduling.application.AppointmentEvents;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Avisa os médicos de alterações à sua agenda e envia o resumo da véspera. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulingNotificationListener {

  static final String REFERENCE_TYPE = "APPOINTMENT";
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

  private final NotificationSender sender;

  @TransactionalEventListener
  public void onCancelled(AppointmentEvents.Cancelled e) {
    deliver(
        e.hospitalId(),
        List.of(e.doctorId()),
        new NotificationContent(
            NotificationType.APPOINTMENT_CANCELLED,
            NotificationPriority.NORMAL,
            "Consulta cancelada — " + when(e.date(), e.startTime()),
            e.patientLabel() + ". Motivo: " + e.reason(),
            link(e.appointmentId()),
            REFERENCE_TYPE,
            e.appointmentId(),
            null));
  }

  @TransactionalEventListener
  public void onRescheduled(AppointmentEvents.Rescheduled e) {
    var recipients = new LinkedHashSet<UUID>(List.of(e.previousDoctorId(), e.newDoctorId()));
    recipients.remove(e.actorId());
    if (recipients.isEmpty()) {
      return;
    }
    deliver(
        e.hospitalId(),
        recipients,
        new NotificationContent(
            NotificationType.APPOINTMENT_RESCHEDULED,
            NotificationPriority.NORMAL,
            "Consulta remarcada — " + e.patientLabel(),
            "De "
                + when(e.previousDate(), e.previousStart())
                + " para "
                + when(e.newDate(), e.newStart())
                + (e.previousDoctorId().equals(e.newDoctorId()) ? "." : ", com outro médico."),
            link(e.newAppointmentId()),
            REFERENCE_TYPE,
            e.newAppointmentId(),
            null));
  }

  @EventListener
  public void onAgendaReminder(AppointmentEvents.AgendaReminder e) {
    deliver(
        e.hospitalId(),
        List.of(e.doctorId()),
        new NotificationContent(
            NotificationType.AGENDA_REMINDER,
            NotificationPriority.LOW,
            "Agenda de amanhã (" + e.date().format(DAY) + ")",
            e.count()
                + (e.count() == 1
                    ? " consulta, às " + e.first()
                    : " consultas, das " + e.first() + " às " + e.last())
                + ".",
            "/scheduling?date=" + e.date() + "&doctorId=" + e.doctorId(),
            null,
            null,
            "AGENDA_REMINDER:" + e.date()));
  }

  private void deliver(UUID hospitalId, java.util.Collection<UUID> to, NotificationContent c) {
    try {
      sender.send(hospitalId, to, c);
    } catch (RuntimeException ex) {
      log.error("Falha ao notificar {}", c.type(), ex);
    }
  }

  private static String when(LocalDate date, LocalTime time) {
    return date.format(DAY) + " às " + time;
  }

  private static String link(UUID appointmentId) {
    return "/scheduling/appointments/" + appointmentId;
  }
}
