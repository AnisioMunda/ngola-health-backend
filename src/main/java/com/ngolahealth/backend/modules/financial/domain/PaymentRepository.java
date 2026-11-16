package com.ngolahealth.backend.modules.financial.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  @Query("SELECT p FROM Payment p WHERE p.invoiceId = :invoiceId ORDER BY p.receivedAt")
  List<Payment> forInvoice(@Param("invoiceId") UUID invoiceId);

  @Query(
      "SELECT p FROM Payment p WHERE p.receivedAt >= :from AND p.receivedAt < :to"
          + " ORDER BY p.receivedAt")
  List<Payment> between(@Param("from") Instant from, @Param("to") Instant to);
}
