package com.ngolahealth.backend.modules.notifications.api;

import com.ngolahealth.backend.modules.notifications.domain.NotificationPriority;
import com.ngolahealth.backend.modules.notifications.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {

  private NotificationDtos() {}

  public record NotificationResponse(
      UUID id,
      NotificationType type,
      NotificationPriority priority,
      String title,
      String message,
      String link,
      String referenceType,
      UUID referenceId,
      Instant createdAt,
      Instant readAt) {}

  public record UnreadCount(long count) {}
}
