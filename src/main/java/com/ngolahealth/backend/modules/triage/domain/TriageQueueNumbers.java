package com.ngolahealth.backend.modules.triage.domain;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Senha seguinte do dia no hospital (atómica; recomeça a 1 em cada dia). */
@Component
@RequiredArgsConstructor
public class TriageQueueNumbers {

  private final EntityManager entityManager;

  @Transactional(propagation = Propagation.MANDATORY)
  public int next(UUID hospitalId, LocalDate day) {
    entityManager
        .createNativeQuery(
            "INSERT INTO triage_queue_sequences (hospital_id, queue_date, counter_value)"
                + " VALUES (:hospital, :day, LAST_INSERT_ID(1))"
                + " ON DUPLICATE KEY UPDATE counter_value = LAST_INSERT_ID(counter_value + 1)")
        .setParameter("hospital", hospitalId.toString())
        .setParameter("day", day)
        .executeUpdate();
    return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult())
        .intValue();
  }
}
