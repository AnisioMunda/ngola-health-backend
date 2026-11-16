package com.ngolahealth.backend.modules.inpatient.domain;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Número de internamento seguinte (INT-AAAA-NNNNNN), atómico, por hospital e ano. */
@Component
@RequiredArgsConstructor
public class AdmissionNumbers {

  private final EntityManager entityManager;

  @Transactional(propagation = Propagation.MANDATORY)
  public String next(UUID hospitalId, int year) {
    entityManager
        .createNativeQuery(
            "INSERT INTO admission_sequences (hospital_id, year, counter_value)"
                + " VALUES (:hospital, :year, LAST_INSERT_ID(1))"
                + " ON DUPLICATE KEY UPDATE counter_value = LAST_INSERT_ID(counter_value + 1)")
        .setParameter("hospital", hospitalId.toString())
        .setParameter("year", year)
        .executeUpdate();
    long n =
        ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult())
            .longValue();
    return "INT-%d-%06d".formatted(year, n);
  }
}
