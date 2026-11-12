package com.ngolahealth.backend.modules.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngolahealth.backend.modules.notifications.domain.NotificationContent;
import com.ngolahealth.backend.modules.notifications.domain.NotificationPriority;
import com.ngolahealth.backend.modules.notifications.domain.NotificationType;
import com.ngolahealth.backend.modules.pharmacy.application.StockAlertsEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationContentTest {

  @Test
  void linkMustBeAnInternalRoute() {
    assertThatThrownBy(() -> content("https://exemplo.com", "t"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(content("/lab/requests/1", "t").link()).isEqualTo("/lab/requests/1");
  }

  @Test
  void longTextIsTruncatedToTheColumnSize() {
    var c = content(null, "x".repeat(300));
    assertThat(c.title()).hasSize(200).endsWith("…");
  }

  @Test
  void stockSummaryIsCriticalWhenSomethingIsOutOfStockOrExpired() {
    var event =
        new StockAlertsEvent(
            UUID.randomUUID(),
            LocalDate.of(2026, 11, 12),
            4,
            1,
            2,
            0,
            30,
            List.of("Amoxicilina", "Paracetamol", "Soro"));
    var c = StockNotificationListener.content(event);
    assertThat(c.priority()).isEqualTo(NotificationPriority.CRITICAL);
    assertThat(c.message())
        .isEqualTo(
            "4 medicamentos no stock mínimo ou abaixo (1 esgotado): Amoxicilina, Paracetamol,"
                + " Soro…; 2 lotes expiram nos próximos 30 dias.");
    assertThat(c.dedupeKey()).isEqualTo("STOCK_ALERTS:2026-11-12");

    var onlyExpiring =
        StockNotificationListener.content(
            new StockAlertsEvent(
                UUID.randomUUID(), LocalDate.of(2026, 11, 12), 0, 0, 1, 0, 30, List.of()));
    assertThat(onlyExpiring.priority()).isEqualTo(NotificationPriority.HIGH);
    assertThat(onlyExpiring.message()).isEqualTo("1 lote expira nos próximos 30 dias.");
  }

  private static NotificationContent content(String link, String title) {
    return new NotificationContent(
        NotificationType.SYSTEM, NotificationPriority.LOW, title, "m", link, null, null, null);
  }
}
