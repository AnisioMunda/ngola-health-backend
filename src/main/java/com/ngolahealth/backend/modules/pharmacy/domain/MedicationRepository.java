package com.ngolahealth.backend.modules.pharmacy.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtrado automaticamente pelo hospital ({@code @TenantId}). */
public interface MedicationRepository extends JpaRepository<Medication, UUID> {

  @Query("SELECT m FROM Medication m WHERE m.id = :id")
  Optional<Medication> findScoped(@Param("id") UUID id);

  @Query("SELECT m FROM Medication m WHERE m.id IN :ids")
  List<Medication> findAllScoped(@Param("ids") Collection<UUID> ids);

  @Query(
      """
      SELECT m FROM Medication m
      WHERE (:active IS NULL OR m.active = :active)
        AND (:pattern IS NULL OR m.name LIKE :pattern ESCAPE '\\'
             OR m.genericName LIKE :pattern ESCAPE '\\')
      """)
  Page<Medication> search(
      @Param("pattern") String pattern, @Param("active") Boolean active, Pageable pageable);

  @Query(
      """
      SELECT COUNT(m) > 0 FROM Medication m
      WHERE m.name = :name AND m.dosageForm = :form
        AND ((:strength IS NULL AND m.strength IS NULL) OR m.strength = :strength)
        AND (:ignoreId IS NULL OR m.id <> :ignoreId)
      """)
  boolean existsSame(
      @Param("name") String name,
      @Param("form") DosageForm form,
      @Param("strength") String strength,
      @Param("ignoreId") UUID ignoreId);

  @Query("SELECT m FROM Medication m WHERE m.active = true ORDER BY m.name")
  List<Medication> findAllActive();
}
