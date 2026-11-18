package com.ngolahealth.backend.modules.equipment.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentFaultRepository extends JpaRepository<EquipmentFault, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT f FROM EquipmentFault f WHERE f.id = :id")
  Optional<EquipmentFault> findForUpdate(@Param("id") UUID id);

  @Query(
      "SELECT f FROM EquipmentFault f WHERE f.equipmentId = :equipmentId ORDER BY f.reportedAt DESC")
  List<EquipmentFault> forEquipment(@Param("equipmentId") UUID equipmentId);

  @Query("SELECT f FROM EquipmentFault f WHERE f.status = 'OPEN' ORDER BY f.reportedAt")
  List<EquipmentFault> open();
}
