package com.ngolahealth.backend.modules.prescriptions.domain;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Número de prescrição seguinte (RX-AAAA-NNNNNN), atómico, por hospital e ano. */
@Component
@RequiredArgsConstructor
public class PrescriptionNumbers {

  private final EntityManager entityManager;

  @Transactional(propagation = Propagation.MANDATORY)
  public String next(UUID hospitalId, int year) {
    entityManager
        .createNativeQuery(
            "INSERT INTO prescription_sequences (hospital_id, year, counter_value)"
                + " VALUES (:hospital, :year, LAST_INSERT_ID(1))"
                + " ON DUPLICATE KEY UPDATE counter_value = LAST_INSERT_ID(counter_value + 1)")
        .setParameter("hospital", hospitalId.toString())
        .setParameter("year", year)
        .executeUpdate();
    long n =
        ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult())
            .longValue();
    return "RX-%d-%06d".formatted(year, n);
  }
}
