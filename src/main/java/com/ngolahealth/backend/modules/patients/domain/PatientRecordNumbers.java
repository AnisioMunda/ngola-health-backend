package com.ngolahealth.backend.modules.patients.domain;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gera o número de processo seguinte de um hospital. Um único comando MySQL incrementa o contador
 * (criando-o se não existir) e bloqueia a linha até ao fim da transacção: dois registos simultâneos
 * nunca recebem o mesmo número. Se a transacção for revertida, o número perde-se (pode haver
 * saltos, nunca repetições).
 */
@Component
@RequiredArgsConstructor
public class PatientRecordNumbers {

  private final EntityManager entityManager;

  @Transactional(propagation = Propagation.MANDATORY)
  public String next(UUID hospitalId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO patient_record_sequences (hospital_id, counter_value)"
                + " VALUES (:hospital, LAST_INSERT_ID(1))"
                + " ON DUPLICATE KEY UPDATE counter_value = LAST_INSERT_ID(counter_value + 1)")
        .setParameter("hospital", hospitalId.toString())
        .executeUpdate();
    long value =
        ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult())
            .longValue();
    return format(value);
  }

  static String format(long value) {
    return String.format("%06d", value);
  }
}
