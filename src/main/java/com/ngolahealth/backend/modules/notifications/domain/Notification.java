package com.ngolahealth.backend.modules.notifications.domain;

import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Notificação para um único utilizador; só o estado "lida" muda depois de criada. */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends HospitalScopedEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "recipient_id", nullable = false, updatable = false, length = 36)
  private UUID recipientId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, updatable = false, length = 40)
  private NotificationType type;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, updatable = false, length = 10)
  private NotificationPriority priority;

  @Column(nullable = false, updatable = false, length = 200)
  private String title;

  @Column(nullable = false, updatable = false, length = 1000)
  private String message;

  @Column(updatable = false, length = 300)
  private String link;

  @Column(name = "reference_type", updatable = false, length = 30)
  private String referenceType;

  @Column(name = "reference_id", updatable = false, length = 36)
  private UUID referenceId;

  @Column(name = "dedupe_key", updatable = false, length = 150)
  private String dedupeKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "read_at")
  private Instant readAt;

  public Notification(UUID recipientId, NotificationContent content, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.recipientId = recipientId;
    this.type = content.type();
    this.priority = content.priority();
    this.title = content.title();
    this.message = content.message();
    this.link = content.link();
    this.referenceType = content.referenceType();
    this.referenceId = content.referenceId();
    this.dedupeKey = content.dedupeKey();
    this.createdAt = createdAt;
  }

  public boolean isRead() {
    return readAt != null;
  }

  /** Idempotente: marcar de novo mantém a hora da primeira leitura. */
  public void markRead(Instant at) {
    if (readAt == null) {
      readAt = at;
    }
  }
}
