package com.ngolahealth.backend.modules.portal.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface ActivationCodeRepository extends JpaRepository<ActivationCode, UUID> {

  Optional<ActivationCode> findByCodeHash(String codeHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM ActivationCode c WHERE c.id = :id")
  Optional<ActivationCode> findForUpdate(@Param("id") UUID id);

  @Query(
      "SELECT c FROM ActivationCode c WHERE c.patientId = :patientId AND c.usedAt IS NULL"
          + " AND c.revokedAt IS NULL ORDER BY c.createdAt DESC")
  List<ActivationCode> pendingFor(@Param("patientId") UUID patientId);

  /** Conta uma tentativa errada fora da transacção da activação (que falha e é desfeita). */
  @Modifying
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Query("UPDATE ActivationCode c SET c.failedAttempts = c.failedAttempts + 1 WHERE c.id = :id")
  void registerFailure(@Param("id") UUID id);
}
