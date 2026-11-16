package com.ngolahealth.backend.modules.financial.domain;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Numeração dos documentos (FT, FR, NC e RC), por hospital, tipo e ano. O contador muda na mesma
 * transacção da emissão: se esta falhar, o número volta atrás e a sequência não tem falhas. O
 * INSERT … ON DUPLICATE KEY bloqueia a linha até ao fim da transacção, serializando emissões.
 */
@Component
@RequiredArgsConstructor
public class DocumentNumbers {

  public record Assigned(int year, long sequence, String number) {}

  private final EntityManager entityManager;

  @Transactional(propagation = Propagation.MANDATORY)
  public Assigned next(UUID hospitalId, String type, int year) {
    entityManager
        .createNativeQuery(
            "INSERT INTO document_sequences (hospital_id, document_type, year, counter_value)"
                + " VALUES (:hospital, :type, :year, LAST_INSERT_ID(1))"
                + " ON DUPLICATE KEY UPDATE counter_value = LAST_INSERT_ID(counter_value + 1)")
        .setParameter("hospital", hospitalId.toString())
        .setParameter("type", type)
        .setParameter("year", year)
        .executeUpdate();
    long n =
        ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult())
            .longValue();
    return new Assigned(year, n, "%s %d/%06d".formatted(type, year, n));
  }
}
