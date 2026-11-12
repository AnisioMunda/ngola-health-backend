package com.ngolahealth.backend.modules.notifications.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Conteúdo de uma notificação, igual para todos os destinatários.
 *
 * @param link rota interna do frontend (começa por "/"); nunca um URL externo
 * @param dedupeKey se preenchida, o mesmo destinatário não recebe duas notificações com a mesma
 *     chave (ex.: {@code STOCK_ALERTS:2026-11-12})
 */
public record NotificationContent(
    NotificationType type,
    NotificationPriority priority,
    String title,
    String message,
    String link,
    String referenceType,
    UUID referenceId,
    String dedupeKey) {

  public NotificationContent {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(priority, "priority");
    title = truncate(Objects.requireNonNull(title, "title").strip(), 200);
    message = truncate(Objects.requireNonNull(message, "message").strip(), 1000);
    if (link != null && !link.startsWith("/")) {
      throw new IllegalArgumentException("O link de uma notificação é uma rota interna: " + link);
    }
  }

  private static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }
}
