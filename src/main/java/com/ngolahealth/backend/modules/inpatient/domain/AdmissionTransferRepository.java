package com.ngolahealth.backend.modules.inpatient.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionTransferRepository extends JpaRepository<AdmissionTransfer, UUID> {

  @Query(
      "SELECT t FROM AdmissionTransfer t WHERE t.admissionId = :admissionId ORDER BY t.transferredAt")
  List<AdmissionTransfer> forAdmission(@Param("admissionId") UUID admissionId);
}
