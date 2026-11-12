package com.ngolahealth.backend.modules.pharmacy.application;

import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.config.properties.NotificationProperties;
import com.ngolahealth.backend.modules.hospitals.application.HospitalService;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.MedicationResponse;
import com.ngolahealth.backend.security.tenant.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Todas as manhãs, calcula os alertas de stock de cada hospital activo e publica um resumo. Um
 * hospital com erro não impede os restantes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockAlertJob {

  private static final int NAMES_IN_MESSAGE = 3;

  private final HospitalService hospitals;
  private final StockService stock;
  private final ApplicationEventPublisher events;
  private final NotificationProperties properties;
  private final AppProperties app;
  private final Clock clock;

  /** Devolve o número de hospitais com alertas. */
  @Scheduled(cron = "${app.notifications.stock-alert-cron}", zone = "${app.time-zone}")
  public int run() {
    var today = LocalDate.ofInstant(clock.instant(), app.timeZone());
    int days = properties.stockExpiryDays();
    int published = 0;
    for (var hospital : hospitals.findActive()) {
      try {
        var alerts = TenantContext.callAs(hospital.id(), () -> stock.snapshot(days));
        var event =
            new StockAlertsEvent(
                hospital.id(),
                today,
                alerts.lowStock().size(),
                (int) alerts.lowStock().stream().filter(m -> m.stockAvailable() == 0).count(),
                alerts.expiringSoon().size(),
                alerts.expired().size(),
                days,
                alerts.lowStock().stream()
                    .map(MedicationResponse::name)
                    .limit(NAMES_IN_MESSAGE)
                    .toList());
        if (!event.isEmpty()) {
          events.publishEvent(event);
          published++;
        }
      } catch (RuntimeException e) {
        log.error("Falha nos alertas de stock do hospital {}", hospital.id(), e);
      }
    }
    return published;
  }
}
