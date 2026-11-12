package com.ngolahealth.backend.modules.pharmacy.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

  @Query(
      "SELECT m FROM StockMovement m WHERE m.medicationId = :medicationId"
          + " ORDER BY m.performedAt DESC, m.id")
  Page<StockMovement> findByMedication(@Param("medicationId") UUID medicationId, Pageable pageable);
}
