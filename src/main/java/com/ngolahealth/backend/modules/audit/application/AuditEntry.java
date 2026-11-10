package com.ngolahealth.backend.modules.audit.application;

import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.audit.domain.AuditOutcome;
import java.util.Map;
import java.util.UUID;

/**
 * Evento a registar.
 *
 * @param hospitalId hospital a que o evento diz respeito (define quem o pode consultar); null =
 *     nível da plataforma, visível só ao SUPER_ADMIN
 * @param changes dados iniciais (criação) ou diferenças {campo: {from, to}} (edição); pode ser null
 * @param actor autor explícito; null = utilizador autenticado no pedido
 */
public record AuditEntry(
    AuditAction action,
    AuditEntityType entityType,
    String entityId,
    UUID hospitalId,
    String summary,
    Map<String, Object> changes,
    AuditOutcome outcome,
    AuditActor actor) {

  public static AuditEntry success(
      AuditAction action, AuditEntityType type, Object entityId, UUID hospitalId, String summary) {
    return new AuditEntry(
        action,
        type,
        entityId == null ? null : entityId.toString(),
        hospitalId,
        summary,
        null,
        AuditOutcome.SUCCESS,
        null);
  }

  public AuditEntry withChanges(Map<String, Object> changes) {
    return new AuditEntry(
        action, entityType, entityId, hospitalId, summary, changes, outcome, actor);
  }

  public AuditEntry withOutcome(AuditOutcome outcome) {
    return new AuditEntry(
        action, entityType, entityId, hospitalId, summary, changes, outcome, actor);
  }

  public AuditEntry withActor(AuditActor actor) {
    return new AuditEntry(
        action, entityType, entityId, hospitalId, summary, changes, outcome, actor);
  }
}
