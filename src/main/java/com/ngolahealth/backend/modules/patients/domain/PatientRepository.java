package com.ngolahealth.backend.modules.patients.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Todas as consultas são filtradas automaticamente pelo hospital do utilizador ({@code @TenantId}).
 * Usar JPQL (e não {@code findById}) para leituras por id, para o filtro se aplicar em qualquer
 * caso.
 */
public interface PatientRepository extends JpaRepository<Patient, UUID> {

  @Query("SELECT p FROM Patient p WHERE p.id = :id")
  Optional<Patient> findScoped(@Param("id") UUID id);

  @Query("SELECT p FROM Patient p WHERE p.id IN :ids")
  List<Patient> findAllScoped(@Param("ids") java.util.Collection<UUID> ids);

  boolean existsByNationalId(String nationalId);

  boolean existsByNationalIdAndIdNot(String nationalId, UUID id);

  boolean existsByHealthCardNumber(String healthCardNumber);

  boolean existsByHealthCardNumberAndIdNot(String healthCardNumber, UUID id);

  /**
   * Pesquisa por nome (sem distinguir acentos nem maiúsculas, pela collation), número de processo,
   * BI, cartão de saúde ou telefone.
   */
  @Query(
      """
      SELECT p FROM Patient p
      WHERE (:active IS NULL OR p.active = :active)
        AND (:pattern IS NULL
             OR p.fullName LIKE :pattern ESCAPE '\\'
             OR p.recordNumber LIKE :pattern ESCAPE '\\'
             OR p.nationalId LIKE :pattern ESCAPE '\\'
             OR p.healthCardNumber LIKE :pattern ESCAPE '\\'
             OR p.phone LIKE :pattern ESCAPE '\\')
      """)
  Page<Patient> search(
      @Param("pattern") String pattern, @Param("active") Boolean active, Pageable pageable);

  /** Candidatos a duplicado: mesmo BI, mesmo telefone, ou mesma data com nome parecido. */
  @Query(
      """
      SELECT p FROM Patient p
      WHERE (:nationalId IS NOT NULL AND p.nationalId = :nationalId)
         OR (:phone IS NOT NULL AND p.phone = :phone)
         OR (p.birthDate = :birthDate AND p.fullName LIKE :namePattern ESCAPE '\\')
      ORDER BY p.fullName
      """)
  List<Patient> findPossibleDuplicates(
      @Param("birthDate") LocalDate birthDate,
      @Param("namePattern") String namePattern,
      @Param("nationalId") String nationalId,
      @Param("phone") String phone,
      Pageable pageable);
}
