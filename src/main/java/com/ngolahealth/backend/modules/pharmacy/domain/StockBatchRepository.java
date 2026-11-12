package com.ngolahealth.backend.modules.pharmacy.domain;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtrado automaticamente pelo hospital ({@code @TenantId}). */
public interface StockBatchRepository extends JpaRepository<StockBatch, UUID> {

  @Query("SELECT b FROM StockBatch b WHERE b.medicationId = :medicationId ORDER BY b.expiryDate")
  List<StockBatch> findByMedication(@Param("medicationId") UUID medicationId);

  /** Lotes utilizáveis por ordem FEFO, bloqueados até ao fim da transacção. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT b FROM StockBatch b
      WHERE b.medicationId = :medicationId AND b.quantityAvailable > 0 AND b.expiryDate >= :today
      ORDER BY b.expiryDate, b.receivedAt
      """)
  List<StockBatch> lockUsableFefo(
      @Param("medicationId") UUID medicationId, @Param("today") LocalDate today);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM StockBatch b WHERE b.id = :id")
  Optional<StockBatch> findForUpdate(@Param("id") UUID id);

  boolean existsByMedicationIdAndBatchNumber(UUID medicationId, String batchNumber);

  /** Saldo utilizável (não expirado) por medicamento. Linhas: [medicationId, soma]. */
  @Query(
      """
      SELECT b.medicationId, SUM(b.quantityAvailable) FROM StockBatch b
      WHERE b.medicationId IN :ids AND b.expiryDate >= :today
      GROUP BY b.medicationId
      """)
  List<Object[]> usableTotals(@Param("ids") Collection<UUID> ids, @Param("today") LocalDate today);

  /** Validade mais próxima com saldo, por medicamento. Linhas: [medicationId, data]. */
  @Query(
      """
      SELECT b.medicationId, MIN(b.expiryDate) FROM StockBatch b
      WHERE b.medicationId IN :ids AND b.expiryDate >= :today AND b.quantityAvailable > 0
      GROUP BY b.medicationId
      """)
  List<Object[]> nextExpiry(@Param("ids") Collection<UUID> ids, @Param("today") LocalDate today);

  /** Lotes com saldo a expirar até {@code until} (inclui os já expirados). */
  @Query(
      """
      SELECT b FROM StockBatch b
      WHERE b.quantityAvailable > 0 AND b.expiryDate <= :until
      ORDER BY b.expiryDate
      """)
  List<StockBatch> withBalanceExpiringBy(@Param("until") LocalDate until);
}
