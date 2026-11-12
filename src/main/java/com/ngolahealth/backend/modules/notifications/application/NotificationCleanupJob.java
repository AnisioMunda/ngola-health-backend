package com.ngolahealth.backend.modules.notifications.application;

import com.ngolahealth.backend.config.properties.NotificationProperties;
import com.ngolahealth.backend.modules.notifications.domain.NotificationRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Apaga diariamente as notificações mais antigas do que o período de retenção. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupJob {

  private final NotificationRepository notifications;
  private final NotificationProperties properties;
  private final Clock clock;

  @Scheduled(cron = "${app.notifications.cleanup-cron}")
  @Transactional
  public int purge() {
    int deleted = notifications.deleteCreatedBefore(clock.instant().minus(properties.retention()));
    if (deleted > 0) {
      log.info("Notificações antigas removidas: {}", deleted);
    }
    return deleted;
  }
}
