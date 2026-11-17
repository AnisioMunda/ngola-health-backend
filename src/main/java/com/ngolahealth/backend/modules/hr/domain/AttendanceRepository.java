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

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM AttendanceRecord a WHERE a.id = :id")
  Optional<AttendanceRecord> findForUpdate(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM AttendanceRecord a WHERE a.userId = :userId AND a.clockOut IS NULL")
  Optional<AttendanceRecord> lockOpen(@Param("userId") UUID userId);

  @Query("SELECT a FROM AttendanceRecord a WHERE a.userId = :userId AND a.clockOut IS NULL")
  Optional<AttendanceRecord> findOpen(@Param("userId") UUID userId);

  @Query(
      """
      SELECT a FROM AttendanceRecord a
      WHERE a.clockIn >= :from AND a.clockIn < :to AND (:userId IS NULL OR a.userId = :userId)
      ORDER BY a.clockIn
      """)
  List<AttendanceRecord> between(
      @Param("from") Instant from, @Param("to") Instant to, @Param("userId") UUID userId);

  @Query("SELECT a FROM AttendanceRecord a WHERE a.shiftId IN :shiftIds")
  List<AttendanceRecord> forShifts(@Param("shiftIds") java.util.Collection<UUID> shiftIds);
}
