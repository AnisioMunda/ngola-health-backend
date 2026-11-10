package com.ngolahealth.backend.modules.audit.api;

import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.audit.domain.AuditOutcome;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AuditDtos {

  private AuditDtos() {}

  /** Filtros da pesquisa; {@code to} é exclusivo. */
  public record AuditSearch(
      String search,
      AuditAction action,
      AuditEntityType entityType,
      String entityId,
      UUID actorId,
      AuditOutcome outcome,
      UUID hospitalId,
      Instant from,
      Instant to) {}

  public record AuditEventResponse(
      UUID id,
      Instant occurredAt,
      UUID hospitalId,
      UUID actorId,
      String actorUsername,
      String actorName,
      AuditAction action,
      AuditEntityType entityType,
      String entityId,
      String summary,
      Map<String, Object> changes,
      AuditOutcome outcome,
      String ipAddress,
      String userAgent,
      String requestMethod,
      String requestPath,
      String requestId) {}
}
