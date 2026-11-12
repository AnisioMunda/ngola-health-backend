package com.ngolahealth.backend.modules.laboratory.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultAmendmentRepository extends JpaRepository<ResultAmendment, UUID> {

  @Query("SELECT a FROM ResultAmendment a WHERE a.itemId IN :itemIds ORDER BY a.amendedAt")
  List<ResultAmendment> findByItems(@Param("itemIds") Collection<UUID> itemIds);
}
