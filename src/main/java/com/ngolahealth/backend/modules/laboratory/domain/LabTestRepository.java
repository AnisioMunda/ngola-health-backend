package com.ngolahealth.backend.modules.laboratory.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabTestRepository extends JpaRepository<LabTest, UUID> {

  @Query("SELECT t FROM LabTest t WHERE t.id = :id")
  Optional<LabTest> findScoped(@Param("id") UUID id);

  @Query("SELECT t FROM LabTest t WHERE t.id IN :ids")
  List<LabTest> findAllScoped(@Param("ids") Collection<UUID> ids);

  @Query(
      """
      SELECT t FROM LabTest t
      WHERE (:active IS NULL OR t.active = :active)
        AND (:pattern IS NULL OR t.name LIKE :pattern ESCAPE '\\' OR t.code LIKE :pattern ESCAPE '\\')
      ORDER BY t.category, t.name
      """)
  List<LabTest> search(@Param("pattern") String pattern, @Param("active") Boolean active);

  @Query(
      "SELECT COUNT(t) > 0 FROM LabTest t WHERE t.code = :code AND (:ignoreId IS NULL OR t.id <> :ignoreId)")
  boolean codeTaken(@Param("code") String code, @Param("ignoreId") UUID ignoreId);
}
