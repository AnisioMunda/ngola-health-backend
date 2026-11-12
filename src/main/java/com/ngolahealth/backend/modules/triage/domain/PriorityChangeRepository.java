package com.ngolahealth.backend.modules.triage.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriorityChangeRepository extends JpaRepository<PriorityChange, UUID> {

  @Query("SELECT c FROM PriorityChange c WHERE c.triageId = :triageId ORDER BY c.changedAt")
  List<PriorityChange> findByTriage(@Param("triageId") UUID triageId);
}
