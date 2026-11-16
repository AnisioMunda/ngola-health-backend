package com.ngolahealth.backend.modules.financial.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServicePriceRepository extends JpaRepository<ServicePrice, UUID> {

  @Query("SELECT p FROM ServicePrice p WHERE p.id = :id")
  Optional<ServicePrice> findScoped(@Param("id") UUID id);

  @Query("SELECT p FROM ServicePrice p WHERE p.id IN :ids")
  List<ServicePrice> findAllScoped(@Param("ids") Collection<UUID> ids);

  @Query(
      """
      SELECT p FROM ServicePrice p
      WHERE (:active IS NULL OR p.active = :active)
        AND (:category IS NULL OR p.category = :category)
        AND (:term IS NULL OR LOWER(p.description) LIKE :term ESCAPE '\\' OR LOWER(p.code) LIKE :term ESCAPE '\\')
      ORDER BY p.category, p.description
      """)
  List<ServicePrice> search(
      @Param("term") String term,
      @Param("category") ServiceCategory category,
      @Param("active") Boolean active);

  @Query(
      "SELECT COUNT(p) > 0 FROM ServicePrice p WHERE p.code = :code"
          + " AND (:excludeId IS NULL OR p.id <> :excludeId)")
  boolean codeTaken(@Param("code") String code, @Param("excludeId") UUID excludeId);
}
