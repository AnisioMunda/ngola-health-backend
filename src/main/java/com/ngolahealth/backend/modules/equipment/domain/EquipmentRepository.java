package com.ngolahealth.backend.modules.equipment.domain;

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

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {

  @Query("SELECT e FROM Equipment e WHERE e.id = :id")
  Optional<Equipment> findScoped(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT e FROM Equipment e WHERE e.id = :id")
  Optional<Equipment> findForUpdate(@Param("id") UUID id);

  @Query(
      """
      SELECT e FROM Equipment e
      WHERE e.status IN :statuses
        AND (:category IS NULL OR e.category = :category)
        AND (:wardId IS NULL OR e.wardId = :wardId)
        AND (:term IS NULL OR LOWER(e.name) LIKE :term ESCAPE '\\' OR LOWER(e.code) LIKE :term ESCAPE '\\'
             OR LOWER(e.serialNumber) LIKE :term ESCAPE '\\')
      """)
  Page<Equipment> search(
      @Param("statuses") Collection<EquipmentStatus> statuses,
      @Param("category") EquipmentCategory category,
      @Param("wardId") UUID wardId,
      @Param("term") String term,
      Pageable pageable);

  /** Manutenção ou calibração vencida ou a vencer até {@code until} (equipamentos não abatidos). */
  @Query(
      """
      SELECT e FROM Equipment e
      WHERE e.status <> 'RETIRED'
        AND ((e.nextMaintenanceDate IS NOT NULL AND e.nextMaintenanceDate <= :until)
             OR (e.nextCalibrationDate IS NOT NULL AND e.nextCalibrationDate <= :until))
      ORDER BY e.name
      """)
  List<Equipment> dueBy(@Param("until") LocalDate until);

  @Query(
      "SELECT COUNT(e) > 0 FROM Equipment e WHERE e.code = :code"
          + " AND (:excludeId IS NULL OR e.id <> :excludeId)")
  boolean codeTaken(@Param("code") String code, @Param("excludeId") UUID excludeId);

  @Query("SELECT e.status, COUNT(e) FROM Equipment e GROUP BY e.status")
  List<Object[]> countByStatus();
}
