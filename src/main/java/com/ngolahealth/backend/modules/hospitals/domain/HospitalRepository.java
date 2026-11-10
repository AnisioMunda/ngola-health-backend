package com.ngolahealth.backend.modules.hospitals.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HospitalRepository extends JpaRepository<Hospital, UUID> {

  List<Hospital> findByActiveTrueOrderByNameAsc();

  boolean existsByCode(String code);

  boolean existsByCodeAndIdNot(String code, UUID id);

  /**
   * Pesquisa por nome, código, província ou município. {@code pattern} já vem em minúsculas, com os
   * caracteres especiais do LIKE escapados e envolvido em {@code %}; null ignora o filtro.
   */
  @Query(
      """
      SELECT h FROM Hospital h
      WHERE (:active IS NULL OR h.active = :active)
        AND (:pattern IS NULL
             OR LOWER(h.name) LIKE :pattern ESCAPE '\\'
             OR LOWER(h.code) LIKE :pattern ESCAPE '\\'
             OR LOWER(h.province) LIKE :pattern ESCAPE '\\'
             OR LOWER(h.municipality) LIKE :pattern ESCAPE '\\')
      """)
  Page<Hospital> search(
      @Param("pattern") String pattern, @Param("active") Boolean active, Pageable pageable);
}
