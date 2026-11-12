package com.ngolahealth.backend.modules.pharmacy.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resumo diário de stock de um hospital, publicado só quando há alguma coisa a assinalar.
 *
 * @param lowStock medicamentos activos no mínimo ou abaixo (inclui os esgotados)
 * @param outOfStock medicamentos activos sem stock utilizável
 * @param lowStockNames alguns nomes, para a mensagem
 */
public record StockAlertsEvent(
    UUID hospitalId,
    LocalDate date,
    int lowStock,
    int outOfStock,
    int expiringSoon,
    int expired,
    int expiryDays,
    List<String> lowStockNames) {

  public boolean isEmpty() {
    return lowStock == 0 && expiringSoon == 0 && expired == 0;
  }
}
