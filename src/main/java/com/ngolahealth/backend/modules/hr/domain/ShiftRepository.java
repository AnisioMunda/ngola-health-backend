package com.ngolahealth.backend.modules.hr.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

  @Query("SELECT s FROM Shift s WHERE s.id = :id")
  Optional<Shift> findScoped(@Param("id") UUID id);

  @Query(
      """
      SELECT s FROM Shift s
      WHERE s.startsAt < :to AND s.endsAt > :from
        AND (:userId IS NULL OR s.userId = :userId)
        AND (:all = true OR s.status = 'SCHEDULED')
      ORDER BY s.startsAt, s.userId
      """)
  List<Shift> between(
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("userId") UUID userId,
      @Param("all") boolean includeCancelled);

  /**
   * Turnos activos do profissional que tocam o período, com leitura bloqueante: dois pedidos
   * simultâneos para a mesma pessoa não criam turnos sobrepostos (no MySQL uma leitura normal veria
   * o retrato do início da transacção).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT s FROM Shift s
      WHERE s.userId = :userId AND s.status = 'SCHEDULED'
        AND s.startsAt < :to AND s.endsAt > :from
      """)
  List<Shift> lockActiveOverlapping(
      @Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to);
}
