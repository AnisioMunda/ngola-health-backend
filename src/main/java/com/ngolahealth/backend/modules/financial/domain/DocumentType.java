package com.ngolahealth.backend.modules.financial.domain;

/** Tipos de documento. RC (recibo) só existe como número de pagamento. */
public enum DocumentType {
  /** Factura: paga depois, com um ou mais recibos. */
  FT,
  /** Factura-recibo: paga no acto da emissão. */
  FR,
  /** Nota de crédito: corrige (anula o valor de) uma FT ou FR emitida. */
  NC
}
