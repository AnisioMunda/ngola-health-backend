package com.ngolahealth.backend.modules.notifications.application;

import com.ngolahealth.backend.modules.notifications.domain.Notification;
import com.ngolahealth.backend.modules.notifications.domain.NotificationContent;
import com.ngolahealth.backend.modules.notifications.domain.NotificationRepository;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.security.tenant.TenantContext;
import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Entrada única para criar notificações (API interna para os outros módulos).
 *
 * <p>Cada envio corre na sua própria transacção, no âmbito do hospital indicado: funciona dentro de
 * um pedido, depois do commit de outro serviço ou numa tarefa agendada. Só recebem utilizadores
 * activos desse hospital, e cada um recebe a sua cópia (o estado "lida" é individual).
 */
@Slf4j
@Service
public class NotificationSender {

  private final NotificationRepository notifications;
  private final StaffDirectory staff;
  private final Clock clock;
  private final TransactionTemplate newTransaction;

  public NotificationSender(
      NotificationRepository notifications,
      StaffDirectory staff,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.notifications = notifications;
    this.staff = staff;
    this.clock = clock;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Envia a cada destinatário activo do hospital. Devolve quantas notificações foram criadas
   * (destinatários inactivos, de outro hospital ou que já têm a mesma chave ficam de fora).
   */
  public int send(UUID hospitalId, Collection<UUID> recipients, NotificationContent content) {
    var valid = new HashSet<>(staff.activeIn(hospitalId, recipients));
    if (valid.isEmpty()) {
      log.debug("Notificação {} sem destinatários válidos; ignorada", content.type());
      return 0;
    }
    Integer created =
        TenantContext.callAs(
            hospitalId,
            () ->
                newTransaction.execute(
                    status -> {
                      if (content.dedupeKey() != null) {
                        valid.removeAll(
                            notifications.recipientsWithKey(content.dedupeKey(), valid));
                      }
                      var now = clock.instant();
                      notifications.saveAll(
                          valid.stream().map(id -> new Notification(id, content, now)).toList());
                      return valid.size();
                    }));
    return created == null ? 0 : created;
  }

  /**
   * Envia aos utilizadores activos com o primeiro perfil da lista que tenha alguém no hospital
   * (ex.: farmacêuticos e, se não houver, administradores).
   */
  public int sendToRole(UUID hospitalId, List<Role> rolesInOrder, NotificationContent content) {
    for (var role : rolesInOrder) {
      var ids =
          staff.listActive(hospitalId, role).stream().map(StaffDirectory.StaffRef::id).toList();
      if (!ids.isEmpty()) {
        return send(hospitalId, ids, content);
      }
    }
    return 0;
  }
}
