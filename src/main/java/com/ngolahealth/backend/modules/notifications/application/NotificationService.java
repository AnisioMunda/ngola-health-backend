package com.ngolahealth.backend.modules.notifications.application;

import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.notifications.api.NotificationDtos.NotificationResponse;
import com.ngolahealth.backend.modules.notifications.api.NotificationDtos.UnreadCount;
import com.ngolahealth.backend.modules.notifications.domain.Notification;
import com.ngolahealth.backend.modules.notifications.domain.NotificationRepository;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificações do utilizador autenticado. Cada um só vê e marca as suas; utilizadores sem hospital
 * (SUPER_ADMIN) não têm notificações.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationRepository notifications;
  private final CurrentUserProvider currentUser;
  private final Clock clock;

  @Transactional(readOnly = true)
  public PageResponse<NotificationResponse> list(boolean unreadOnly, int page, int size) {
    var me = currentUser.get().id();
    return PageResponse.of(
        notifications.findForRecipient(me, unreadOnly, PageRequest.of(page, size)),
        NotificationService::toResponse);
  }

  @Transactional(readOnly = true)
  public UnreadCount unreadCount() {
    return new UnreadCount(notifications.countUnread(currentUser.get().id()));
  }

  @Transactional
  public NotificationResponse markRead(UUID id) {
    var n =
        notifications
            .findForRecipient(id, currentUser.get().id())
            .orElseThrow(
                () ->
                    new NotFoundException("NOTIFICATION_NOT_FOUND", "Notificação não encontrada."));
    n.markRead(clock.instant());
    return toResponse(n);
  }

  @Transactional
  public UnreadCount markAllRead() {
    var me = currentUser.get().id();
    notifications.markAllRead(me, clock.instant());
    return new UnreadCount(notifications.countUnread(me));
  }

  static NotificationResponse toResponse(Notification n) {
    return new NotificationResponse(
        n.getId(),
        n.getType(),
        n.getPriority(),
        n.getTitle(),
        n.getMessage(),
        n.getLink(),
        n.getReferenceType(),
        n.getReferenceId(),
        n.getCreatedAt(),
        n.getReadAt());
  }
}
