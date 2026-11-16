package com.ngolahealth.backend.modules.inpatient.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WardRepository extends JpaRepository<Ward, UUID> {

  @Query("SELECT w FROM Ward w WHERE w.id = :id")
  Optional<Ward> findScoped(@Param("id") UUID id);

  @Query("SELECT w FROM Ward w WHERE (:all = true OR w.active = true) ORDER BY w.name")
  List<Ward> list(@Param("all") boolean includeInactive);

  @Query("SELECT w FROM Ward w WHERE w.id IN :ids")
  List<Ward> findAllScoped(@Param("ids") java.util.Collection<UUID> ids);

  @Query(
      "SELECT COUNT(w) > 0 FROM Ward w WHERE w.code = :code AND (:excludeId IS NULL OR w.id <> :excludeId)")
  boolean codeTaken(@Param("code") String code, @Param("excludeId") UUID excludeId);
}
