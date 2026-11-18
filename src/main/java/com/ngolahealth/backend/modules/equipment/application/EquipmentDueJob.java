package com.ngolahealth.backend.modules.equipment.application;

import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.modules.equipment.domain.Equipment;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentRepository;
import com.ngolahealth.backend.modules.hospitals.application.HospitalService;
import com.ngolahealth.backend.security.tenant.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Todas as manhãs, um resumo por hospital das manutenções/calibrações vencidas ou a vencer em 7
 * dias.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EquipmentDueJob {

  static final int HORIZON_DAYS = 7;

  private final HospitalService hospitals;
  private final EquipmentRepository equipment;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transactions;
  private final AppProperties app;
  private final Clock clock;

  @Scheduled(cron = "${app.equipment.due-cron:0 0 7 * * *}", zone = "${app.time-zone}")
  public int run() {
    var today = LocalDate.ofInstant(clock.instant(), app.timeZone());
    int published = 0;
    for (var hospital : hospitals.findActive()) {
      try {
        List<Equipment> due =
            TenantContext.callAs(
                hospital.id(),
                () -> transactions.execute(s -> equipment.dueBy(today.plusDays(HORIZON_DAYS))));
        if (due == null || due.isEmpty()) {
          continue;
        }
        int overdue =
            (int)
                due.stream()
                    .filter(
                        e ->
                            EquipmentSupport.overdue(e.getNextMaintenanceDate(), today)
                                || EquipmentSupport.overdue(e.getNextCalibrationDate(), today))
                    .count();
        events.publishEvent(
            new EquipmentEvents.MaintenanceDue(
                hospital.id(),
                today,
                overdue,
                due.size() - overdue,
                due.stream()
                    .sorted(EquipmentService.BY_CRITICALITY)
                    .map(Equipment::getName)
                    .limit(3)
                    .toList()));
        published++;
      } catch (RuntimeException e) {
        log.error("Falha no resumo de manutenções do hospital {}", hospital.id(), e);
      }
    }
    return published;
  }
}
