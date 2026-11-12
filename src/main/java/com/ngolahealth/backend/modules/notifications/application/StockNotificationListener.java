package com.ngolahealth.backend.modules.notifications.application;

import com.ngolahealth.backend.modules.notifications.domain.NotificationContent;
import com.ngolahealth.backend.modules.notifications.domain.NotificationPriority;
import com.ngolahealth.backend.modules.notifications.domain.NotificationType;
import com.ngolahealth.backend.modules.pharmacy.application.StockAlertsEvent;
import com.ngolahealth.backend.security.Role;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Um único resumo por dia para cada farmacêutico (ou, se o hospital não tiver farmacêuticos, para
 * os administradores), em vez de uma notificação por medicamento e por lote como no modelo.
 */
@Component
@RequiredArgsConstructor
public class StockNotificationListener {

  static final List<Role> RECIPIENTS = List.of(Role.PHARMACIST, Role.ADMIN);

  private final NotificationSender sender;

  @EventListener
  public void onStockAlerts(StockAlertsEvent e) {
    sender.sendToRole(e.hospitalId(), RECIPIENTS, content(e));
  }

  static NotificationContent content(StockAlertsEvent e) {
    var parts = new ArrayList<String>();
    if (e.lowStock() > 0) {
      parts.add(
          e.lowStock()
              + (e.lowStock() == 1
                  ? " medicamento no stock mínimo ou abaixo"
                  : " medicamentos no stock mínimo ou abaixo")
              + (e.outOfStock() > 0
                  ? " (" + e.outOfStock() + (e.outOfStock() == 1 ? " esgotado)" : " esgotados)")
                  : "")
              + ": "
              + String.join(", ", e.lowStockNames())
              + (e.lowStock() > e.lowStockNames().size() ? "…" : ""));
    }
    if (e.expiringSoon() > 0) {
      parts.add(
          e.expiringSoon()
              + (e.expiringSoon() == 1 ? " lote expira" : " lotes expiram")
              + " nos próximos "
              + e.expiryDays()
              + " dias");
    }
    if (e.expired() > 0) {
      parts.add(
          e.expired()
              + (e.expired() == 1
                  ? " lote expirado ainda com saldo"
                  : " lotes expirados ainda com saldo"));
    }
    boolean critical = e.outOfStock() > 0 || e.expired() > 0;
    return new NotificationContent(
        NotificationType.STOCK_ALERTS,
        critical ? NotificationPriority.CRITICAL : NotificationPriority.HIGH,
        critical ? "Stock: acção urgente" : "Stock: alertas do dia",
        String.join("; ", parts) + ".",
        "/pharmacy/alerts",
        "STOCK",
        null,
        "STOCK_ALERTS:" + e.date());
  }
}
