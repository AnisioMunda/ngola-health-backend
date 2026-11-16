package com.ngolahealth.backend.modules.financial.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

  @Query("SELECT i FROM Invoice i WHERE i.id = :id")
  Optional<Invoice> findScoped(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM Invoice i WHERE i.id = :id")
  Optional<Invoice> findForUpdate(@Param("id") UUID id);

  @Query("SELECT i FROM Invoice i WHERE i.relatedInvoiceId = :id")
  Optional<Invoice> findCreditNoteOf(@Param("id") UUID id);

  @Query(
      """
      SELECT i FROM Invoice i
      WHERE i.status IN :statuses
        AND i.documentType IN :types
        AND (:patientId IS NULL OR i.patientId = :patientId)
        AND (:episodeId IS NULL OR i.episodeId = :episodeId)
        AND (:number IS NULL OR i.number LIKE :number)
        AND (:from IS NULL OR i.createdAt >= :from)
        AND (:to IS NULL OR i.createdAt < :to)
      """)
  Page<Invoice> search(
      @Param("statuses") Collection<InvoiceStatus> statuses,
      @Param("types") Collection<DocumentType> types,
      @Param("patientId") UUID patientId,
      @Param("episodeId") UUID episodeId,
      @Param("number") String number,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}
