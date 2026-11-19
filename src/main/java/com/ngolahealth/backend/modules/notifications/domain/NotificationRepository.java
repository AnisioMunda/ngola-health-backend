package com.ngolahealth.backend.modules.notifications.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Consultas filtradas pelo hospital (Hibernate) e sempre pelo destinatário. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  @Query(
      """
      SELECT n FROM Notification n
      WHERE n.recipientId = :recipientId AND (:unreadOnly = false OR n.readAt IS NULL)
      ORDER BY n.createdAt DESC, n.id DESC
      """)
  Page<Notification> findForRecipient(
      @Param("recipientId") UUID recipientId,
      @Param("unreadOnly") boolean unreadOnly,
      Pageable pageable);

  @Query(
      "SELECT COUNT(n) FROM Notification n WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
  long countUnread(@Param("recipientId") UUID recipientId);

  @Query("SELECT n FROM Notification n WHERE n.id = :id AND n.recipientId = :recipientId")
  Optional<Notification> findForRecipient(
      @Param("id") UUID id, @Param("recipientId") UUID recipientId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "UPDATE Notification n SET n.readAt = :at"
          + " WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
  int markAllRead(@Param("recipientId") UUID recipientId, @Param("at") Instant at);

  /** Destinatários (de entre os indicados) que já têm uma notificação com esta chave. */
  @Query(
      "SELECT n.recipientId FROM Notification n"
          + " WHERE n.dedupeKey = :key AND n.recipientId IN :recipients")
  Set<UUID> recipientsWithKey(
      @Param("key") String key, @Param("recipients") Collection<UUID> recipients);

  /**
   * Retenção, para todos os hospitais: consulta nativa de propósito, porque a tarefa agendada não
   * tem hospital e o filtro do Hibernate não apagaria nada.
   */
  @Modifying
  @Query(value = "DELETE FROM notifications WHERE created_at < :cutoff", nativeQuery = true)
  int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
