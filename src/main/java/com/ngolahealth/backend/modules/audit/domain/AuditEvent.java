package com.ngolahealth.backend.modules.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Evento de auditoria. Imutável: o Hibernate nunca emite UPDATE para esta entidade. */
@Entity
@Immutable
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "hospital_id", length = 36)
  private UUID hospitalId;

  @Column(name = "actor_id", length = 36)
  private UUID actorId;

  @Column(name = "actor_username", length = 100)
  private String actorUsername;

  @Column(name = "actor_name", length = 200)
  private String actorName;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 30)
  private AuditAction action;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "entity_type", nullable = false, length = 30)
  private AuditEntityType entityType;

  @Column(name = "entity_id", length = 64)
  private String entityId;

  @Column(nullable = false, length = 500)
  private String summary;

  /** JSON em texto; a coluna é do tipo JSON no MySQL (valida o conteúdo). */
  @Column(columnDefinition = "json")
  private String changes;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private AuditOutcome outcome;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @Column(name = "request_method", length = 10)
  private String requestMethod;

  @Column(name = "request_path", length = 300)
  private String requestPath;

  @Column(name = "request_id", length = 36)
  private String requestId;

  @Builder
  private AuditEvent(
      Instant occurredAt,
      UUID hospitalId,
      UUID actorId,
      String actorUsername,
      String actorName,
      AuditAction action,
      AuditEntityType entityType,
      String entityId,
      String summary,
      String changes,
      AuditOutcome outcome,
      String ipAddress,
      String userAgent,
      String requestMethod,
      String requestPath,
      String requestId) {
    this.id = UUID.randomUUID();
    this.occurredAt = occurredAt;
    this.hospitalId = hospitalId;
    this.actorId = actorId;
    this.actorUsername = truncate(actorUsername, 100);
    this.actorName = truncate(actorName, 200);
    this.action = action;
    this.entityType = entityType;
    this.entityId = truncate(entityId, 64);
    this.summary = truncate(summary, 500);
    this.changes = changes;
    this.outcome = outcome;
    this.ipAddress = truncate(ipAddress, 45);
    this.userAgent = truncate(userAgent, 255);
    this.requestMethod = truncate(requestMethod, 10);
    this.requestPath = truncate(requestPath, 300);
    this.requestId = truncate(requestId, 36);
  }

  private static String truncate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
