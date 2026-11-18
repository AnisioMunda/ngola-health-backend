package com.ngolahealth.backend.modules.equipment.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, UUID> {

  @Query(
      "SELECT m FROM MaintenanceRecord m WHERE m.equipmentId = :equipmentId"
          + " ORDER BY m.performedOn DESC, m.recordedAt DESC")
  List<MaintenanceRecord> forEquipment(@Param("equipmentId") UUID equipmentId);
}
