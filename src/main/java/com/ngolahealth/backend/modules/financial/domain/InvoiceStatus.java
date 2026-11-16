package com.ngolahealth.backend.modules.financial.domain;

/**
 * DRAFT → ISSUED → PARTIALLY_PAID → PAID (FT); DRAFT → PAID (FR, paga na emissão). Emitida sem
 * pagamentos → CANCELLED (anulação); emitida ou paga → CREDITED (nota de crédito).
 */
public enum InvoiceStatus {
  DRAFT,
  ISSUED,
  PARTIALLY_PAID,
  PAID,
  CANCELLED,
  CREDITED
}
