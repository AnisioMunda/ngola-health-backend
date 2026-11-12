package com.ngolahealth.backend.modules.notifications.domain;

/** Tipo de notificação. Novos módulos acrescentam os seus tipos aqui. */
public enum NotificationType {
  /** Todos os exames de um pedido foram validados. */
  LAB_RESULTS_READY,
  /** Resultado alterado validado antes de o pedido estar concluído. */
  LAB_ABNORMAL_RESULT,
  /** Resultado já validado foi corrigido. */
  LAB_RESULT_AMENDED,
  /** Resumo diário de stock baixo, esgotado ou a expirar. */
  STOCK_ALERTS,
  SYSTEM
}
