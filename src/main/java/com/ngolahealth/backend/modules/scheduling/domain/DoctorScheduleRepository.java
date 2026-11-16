package com.ngolahealth.backend.modules.scheduling.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, UUID> {

  @Query("SELECT s FROM DoctorSchedule s WHERE s.id = :id")
  Optional<DoctorSchedule> findScoped(@Param("id") UUID id);

  @Query(
      """
      SELECT s FROM DoctorSchedule s
      WHERE (:doctorId IS NULL OR s.doctorId = :doctorId) AND (:all = true OR s.active = true)
      ORDER BY s.doctorId, s.dayOfWeek, s.startTime
      """)
  List<DoctorSchedule> search(@Param("doctorId") UUID doctorId, @Param("all") boolean all);

  @Query(
      """
      SELECT s FROM DoctorSchedule s
      WHERE s.doctorId = :doctorId AND s.dayOfWeek = :day AND s.active = true
      ORDER BY s.startTime
      """)
  List<DoctorSchedule> activeFor(@Param("doctorId") UUID doctorId, @Param("day") int day);

  /**
   * Bloqueia os horários do médico nesse dia da semana: as marcações concorrentes para o mesmo
   * médico e dia ficam em fila, e a contagem de lugares nunca é ultrapassada.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT s FROM DoctorSchedule s
      WHERE s.doctorId = :doctorId AND s.dayOfWeek = :day AND s.active = true
      ORDER BY s.startTime
      """)
  List<DoctorSchedule> lockFor(@Param("doctorId") UUID doctorId, @Param("day") int day);
}
