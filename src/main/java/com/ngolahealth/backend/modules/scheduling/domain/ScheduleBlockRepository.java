package com.ngolahealth.backend.modules.scheduling.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleBlockRepository extends JpaRepository<ScheduleBlock, UUID> {

  @Query("SELECT b FROM ScheduleBlock b WHERE b.id = :id")
  Optional<ScheduleBlock> findScoped(@Param("id") UUID id);

  @Query(
      """
      SELECT b FROM ScheduleBlock b
      WHERE (:doctorId IS NULL OR b.doctorId = :doctorId)
        AND b.blockDate BETWEEN :from AND :to
      ORDER BY b.blockDate, b.startTime
      """)
  List<ScheduleBlock> between(
      @Param("doctorId") UUID doctorId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
