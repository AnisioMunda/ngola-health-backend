package com.ngolahealth.backend.modules.audit.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

  /**
   * Pesquisa de eventos. Todos os filtros são opcionais (null = ignorar). {@code hospitalId} null
   * só é permitido ao SUPER_ADMIN; {@code to} é exclusivo.
   */
  @Query(
      """
      SELECT e FROM AuditEvent e
      WHERE (:hospitalId IS NULL OR e.hospitalId = :hospitalId)
        AND (:action IS NULL OR e.action = :action)
        AND (:entityType IS NULL OR e.entityType = :entityType)
        AND (:entityId IS NULL OR e.entityId = :entityId)
        AND (:actorId IS NULL OR e.actorId = :actorId)
        AND (:outcome IS NULL OR e.outcome = :outcome)
        AND (:from IS NULL OR e.occurredAt >= :from)
        AND (:to IS NULL OR e.occurredAt < :to)
        AND (:pattern IS NULL
             OR LOWER(e.summary) LIKE :pattern ESCAPE '\\'
             OR LOWER(e.actorUsername) LIKE :pattern ESCAPE '\\'
             OR LOWER(e.actorName) LIKE :pattern ESCAPE '\\')
      """)
  Page<AuditEvent> search(
      @Param("hospitalId") UUID hospitalId,
      @Param("action") AuditAction action,
      @Param("entityType") AuditEntityType entityType,
      @Param("entityId") String entityId,
      @Param("actorId") UUID actorId,
      @Param("outcome") AuditOutcome outcome,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("pattern") String pattern,
      Pageable pageable);
}
