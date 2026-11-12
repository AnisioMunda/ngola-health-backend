package com.ngolahealth.backend.modules.pharmacy.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Única forma de alterar saldos: actualiza o lote e regista o movimento na mesma transacção. O lote
 * tem de ter sido lido com bloqueio ({@code findForUpdate}) por quem chama.
 */
@Component
@RequiredArgsConstructor
public class StockLedger {

  private final StockMovementRepository movements;

  @Transactional(propagation = Propagation.MANDATORY)
  public StockMovement post(
      StockBatch batch,
      MovementType type,
      int units,
      MovementContext context,
      UUID performedBy,
      Instant at) {
    if (units <= 0) {
      throw new IllegalArgumentException("A quantidade de um movimento tem de ser positiva");
    }
    int signed = type.isInbound() ? units : -units;
    int balance = batch.change(signed);
    return movements.save(
        new StockMovement(batch, type, signed, balance, context, performedBy, at));
  }

  /** Movimento de entrada inicial de um lote recebido (o saldo já é a quantidade recebida). */
  @Transactional(propagation = Propagation.MANDATORY)
  public StockMovement postReceipt(StockBatch batch, String reason, UUID performedBy, Instant at) {
    return movements.save(
        new StockMovement(
            batch,
            MovementType.RECEIPT,
            batch.getQuantityReceived(),
            batch.getQuantityAvailable(),
            MovementContext.reason(reason),
            performedBy,
            at));
  }
}
