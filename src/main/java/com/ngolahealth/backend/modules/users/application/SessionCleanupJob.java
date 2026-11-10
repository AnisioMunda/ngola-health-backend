package com.ngolahealth.backend.modules.users.application;

import com.ngolahealth.backend.modules.users.domain.UserSessionRepository;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Apaga diariamente sessões expiradas ou revogadas há mais de 7 dias. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCleanupJob {

  static final Duration RETENTION = Duration.ofDays(7);

  private final UserSessionRepository sessions;
  private final Clock clock;

  @Scheduled(cron = "${app.auth.session-cleanup-cron:0 30 3 * * *}")
  @Transactional
  public int purge() {
    int deleted = sessions.deleteStale(clock.instant().minus(RETENTION));
    if (deleted > 0) {
      log.info("Sessões antigas removidas: {}", deleted);
    }
    return deleted;
  }
}
