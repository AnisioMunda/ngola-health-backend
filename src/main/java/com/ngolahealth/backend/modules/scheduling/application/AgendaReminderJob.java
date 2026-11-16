package com.ngolahealth.backend.modules.scheduling.application;

import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.modules.hospitals.application.HospitalService;
import com.ngolahealth.backend.modules.scheduling.domain.Appointment;
import com.ngolahealth.backend.modules.scheduling.domain.AppointmentRepository;
import com.ngolahealth.backend.modules.scheduling.domain.AppointmentStatus;
import com.ngolahealth.backend.security.tenant.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Na véspera, cada médico recebe um resumo da agenda do dia seguinte (uma notificação por médico, e
 * não uma por consulta como no modelo).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgendaReminderJob {

  private final HospitalService hospitals;
  private final AppointmentRepository appointments;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transactions;
  private final AppProperties app;
  private final Clock clock;

  /** Devolve o número de lembretes publicados (médicos com consultas amanhã). */
  @Scheduled(cron = "${app.scheduling.reminder-cron}", zone = "${app.time-zone}")
  public int run() {
    var tomorrow = LocalDate.ofInstant(clock.instant(), app.timeZone()).plusDays(1);
    int published = 0;
    for (var hospital : hospitals.findActive()) {
      try {
        var reminders =
            TenantContext.callAs(
                hospital.id(),
                () ->
                    transactions.execute(
                        status ->
                            appointments
                                .inPeriod(
                                    null,
                                    tomorrow,
                                    tomorrow,
                                    List.of(
                                        AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED))
                                .stream()
                                .collect(Collectors.groupingBy(Appointment::getDoctorId))
                                .entrySet()
                                .stream()
                                .map(
                                    e ->
                                        new AppointmentEvents.AgendaReminder(
                                            hospital.id(),
                                            e.getKey(),
                                            tomorrow,
                                            e.getValue().size(),
                                            e.getValue().getFirst().getStartTime(),
                                            e.getValue().getLast().getStartTime()))
                                .toList()));
        for (var reminder :
            reminders == null ? new ArrayList<AppointmentEvents.AgendaReminder>() : reminders) {
          events.publishEvent(reminder);
          published++;
        }
      } catch (RuntimeException e) {
        log.error("Falha nos lembretes de agenda do hospital {}", hospital.id(), e);
      }
    }
    return published;
  }
}
