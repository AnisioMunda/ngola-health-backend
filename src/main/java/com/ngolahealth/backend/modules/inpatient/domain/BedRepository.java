package com.ngolahealth.backend.modules.inpatient.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BedRepository extends JpaRepository<Bed, UUID> {

  @Query("SELECT b FROM Bed b WHERE b.id = :id")
  Optional<Bed> findScoped(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM Bed b WHERE b.id = :id")
  Optional<Bed> findForUpdate(@Param("id") UUID id);

  @Query("SELECT b FROM Bed b WHERE b.id IN :ids")
  List<Bed> findAllScoped(@Param("ids") Collection<UUID> ids);

  @Query(
      "SELECT b FROM Bed b WHERE (:wardId IS NULL OR b.wardId = :wardId)"
          + " AND (:all = true OR b.active = true) ORDER BY b.code")
  List<Bed> list(@Param("wardId") UUID wardId, @Param("all") boolean includeInactive);

  @Query(
      "SELECT COUNT(b) > 0 FROM Bed b WHERE b.wardId = :wardId AND b.code = :code"
          + " AND (:excludeId IS NULL OR b.id <> :excludeId)")
  boolean codeTaken(
      @Param("wardId") UUID wardId, @Param("code") String code, @Param("excludeId") UUID excludeId);
}
