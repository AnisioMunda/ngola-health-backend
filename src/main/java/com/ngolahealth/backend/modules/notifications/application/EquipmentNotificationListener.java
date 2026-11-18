package com.ngolahealth.backend.modules.notifications.application;

import com.ngolahealth.backend.modules.equipment.application.EquipmentEvents;
import com.ngolahealth.backend.modules.notifications.domain.NotificationContent;
import com.ngolahealth.backend.modules.notifications.domain.NotificationPriority;
import com.ngolahealth.backend.modules.notifications.domain.NotificationType;
import com.ngolahealth.backend.security.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Avarias e manutenções em atraso: avisa a direcção (ou, sem gestores, os administradores). */
@Slf4j
@Component
@RequiredArgsConstructor
public class EquipmentNotificationListener {

  static final List<Role> RECIPIENTS = List.of(Role.MANAGER, Role.ADMIN);

  private final NotificationSender sender;

  @TransactionalEventListener
  public void onFault(EquipmentEvents.FaultReported e) {
    try {
      sender.sendToRole(
          e.hospitalId(),
          RECIPIENTS,
          new NotificationContent(
              NotificationType.EQUIPMENT_FAULT,
              e.critical()
                  ? NotificationPriority.CRITICAL
                  : e.outOfService() ? NotificationPriority.HIGH : NotificationPriority.NORMAL,
              "Avaria — " + e.equipmentLabel() + (e.outOfService() ? " (fora de serviço)" : ""),
              e.description(),
              "/equipment/" + e.equipmentId(),
              "EQUIPMENT",
              e.equipmentId(),
              null));
    } catch (RuntimeException ex) {
      log.error("Falha ao notificar avaria do equipamento {}", e.equipmentId(), ex);
    }
  }

  @EventListener
  public void onDue(EquipmentEvents.MaintenanceDue e) {
    var parts = new java.util.ArrayList<String>();
    if (e.overdue() > 0) {
      parts.add(e.overdue() + " em atraso");
    }
    if (e.dueSoon() > 0) {
      parts.add(e.dueSoon() + " nos próximos 7 dias");
    }
    sender.sendToRole(
        e.hospitalId(),
        RECIPIENTS,
        new NotificationContent(
            NotificationType.EQUIPMENT_DUE,
            e.overdue() > 0 ? NotificationPriority.HIGH : NotificationPriority.NORMAL,
            "Manutenções de equipamentos",
            String.join(", ", parts)
                + ": "
                + String.join(", ", e.names())
                + (e.overdue() + e.dueSoon() > e.names().size() ? "…" : "")
                + ".",
            "/equipment?due=true",
            null,
            null,
            "EQUIPMENT_DUE:" + e.date()));
  }
}
