package com.ngolahealth.backend.modules.hr.domain;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT l FROM LeaveRequest l WHERE l.id = :id")
  Optional<LeaveRequest> findForUpdate(@Param("id") UUID id);

  @Query(
      """
      SELECT l FROM LeaveRequest l
      WHERE l.status IN :statuses
        AND (:userId IS NULL OR l.userId = :userId)
        AND (:from IS NULL OR l.endDate >= :from)
        AND (:to IS NULL OR l.startDate <= :to)
      """)
  Page<LeaveRequest> search(
      @Param("statuses") Collection<LeaveStatus> statuses,
      @Param("userId") UUID userId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      Pageable pageable);

  /** Pedidos activos (pendentes ou aprovados) do profissional que tocam o período; bloqueante. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT l FROM LeaveRequest l
      WHERE l.userId = :userId AND l.status IN ('PENDING', 'APPROVED')
        AND l.startDate <= :to AND l.endDate >= :from
      """)
  List<LeaveRequest> lockActiveOverlapping(
      @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query(
      """
      SELECT l FROM LeaveRequest l
      WHERE l.status = 'APPROVED' AND l.startDate <= :to AND l.endDate >= :from
        AND (:userId IS NULL OR l.userId = :userId)
      """)
  List<LeaveRequest> approvedBetween(
      @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("SELECT COUNT(l) FROM LeaveRequest l WHERE l.status = 'PENDING'")
  long countPending();
}
