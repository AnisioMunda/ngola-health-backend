package com.ngolahealth.backend.modules.notifications.application;

import com.ngolahealth.backend.modules.hr.application.HrEvents;
import com.ngolahealth.backend.modules.notifications.domain.NotificationContent;
import com.ngolahealth.backend.modules.notifications.domain.NotificationPriority;
import com.ngolahealth.backend.modules.notifications.domain.NotificationType;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.security.Role;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Pedidos de ausência: avisa os gestores; decisões: avisa o requerente. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HrNotificationListener {

  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final NotificationSender sender;
  private final StaffDirectory staff;

  @TransactionalEventListener
  public void onRequested(HrEvents.LeaveRequested e) {
    try {
      // Gestores (ou, sem gestores, administradores), excepto o próprio requerente.
      var recipients =
          staff.listActive(e.hospitalId(), Role.MANAGER).stream()
              .map(StaffDirectory.StaffRef::id)
              .toList();
      if (recipients.stream().allMatch(id -> id.equals(e.userId()))) {
        recipients =
            staff.listActive(e.hospitalId(), Role.ADMIN).stream()
                .map(StaffDirectory.StaffRef::id)
                .toList();
      }
      sender.send(
          e.hospitalId(),
          recipients.stream().filter(id -> !id.equals(e.userId())).toList(),
          new NotificationContent(
              NotificationType.LEAVE_REQUESTED,
              NotificationPriority.NORMAL,
              "Pedido de ausência — " + e.userName(),
              e.type() + " de " + e.start().format(DAY) + " a " + e.end().format(DAY) + ".",
              "/hr/leaves",
              "LEAVE_REQUEST",
              e.leaveId(),
              null));
    } catch (RuntimeException ex) {
      log.error("Falha ao notificar pedido de ausência {}", e.leaveId(), ex);
    }
  }

  @TransactionalEventListener
  public void onDecided(HrEvents.LeaveDecided e) {
    try {
      sender.send(
          e.hospitalId(),
          List.of(e.userId()),
          new NotificationContent(
              NotificationType.LEAVE_DECIDED,
              e.approved() ? NotificationPriority.NORMAL : NotificationPriority.HIGH,
              (e.approved() ? "Ausência aprovada" : "Ausência recusada")
                  + " ("
                  + e.start().format(DAY)
                  + " a "
                  + e.end().format(DAY)
                  + ")",
              e.note() == null
                  ? (e.approved() ? "Pedido aprovado." : "Pedido recusado.")
                  : e.note(),
              "/hr/leaves",
              "LEAVE_REQUEST",
              e.leaveId(),
              null));
    } catch (RuntimeException ex) {
      log.error("Falha ao notificar decisão de ausência {}", e.leaveId(), ex);
    }
  }
}
