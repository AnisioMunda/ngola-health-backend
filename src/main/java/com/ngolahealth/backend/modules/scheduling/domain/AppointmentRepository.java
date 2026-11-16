package com.ngolahealth.backend.modules.scheduling.domain;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
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

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

  /** Estados que ocupam a vaga. */
  Collection<AppointmentStatus> OCCUPYING =
      List.of(
          AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED, AppointmentStatus.CHECKED_IN);

  @Query("SELECT a FROM Appointment a WHERE a.id = :id")
  Optional<Appointment> findScoped(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM Appointment a WHERE a.id = :id")
  Optional<Appointment> findForUpdate(@Param("id") UUID id);

  @Query(
      """
      SELECT a FROM Appointment a
      WHERE a.date BETWEEN :from AND :to
        AND (:doctorId IS NULL OR a.doctorId = :doctorId)
        AND (:patientId IS NULL OR a.patientId = :patientId)
        AND a.status IN :statuses
      """)
  Page<Appointment> search(
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("doctorId") UUID doctorId,
      @Param("patientId") UUID patientId,
      @Param("statuses") Collection<AppointmentStatus> statuses,
      Pageable pageable);

  /** Marcações que ocupam vagas no período (para disponibilidade e calendário). */
  @Query(
      """
      SELECT a FROM Appointment a
      WHERE a.date BETWEEN :from AND :to
        AND (:doctorId IS NULL OR a.doctorId = :doctorId)
        AND a.status IN :statuses
      ORDER BY a.date, a.startTime, a.slotPosition
      """)
  List<Appointment> inPeriod(
      @Param("doctorId") UUID doctorId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("statuses") Collection<AppointmentStatus> statuses);

  /**
   * Marcações que ocupam a vaga, com leitura bloqueante. No MySQL (REPEATABLE READ) uma leitura
   * normal veria o "retrato" do início da transacção, anterior à marcação que acabou de entrar
   * enquanto se esperava pelo bloqueio do horário; a leitura bloqueante vê sempre o estado actual.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT a FROM Appointment a
      WHERE a.doctorId = :doctorId AND a.date = :date AND a.startTime = :start
        AND a.status IN :statuses
      """)
  List<Appointment> lockInSlot(
      @Param("doctorId") UUID doctorId,
      @Param("date") LocalDate date,
      @Param("start") LocalTime start,
      @Param("statuses") Collection<AppointmentStatus> statuses);

  /** Leitura bloqueante, pela mesma razão de {@link #lockInSlot}. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT a FROM Appointment a
      WHERE a.patientId = :patientId AND a.date = :date AND a.status IN :statuses
      """)
  List<Appointment> lockForPatientOn(
      @Param("patientId") UUID patientId,
      @Param("date") LocalDate date,
      @Param("statuses") Collection<AppointmentStatus> statuses);
}
