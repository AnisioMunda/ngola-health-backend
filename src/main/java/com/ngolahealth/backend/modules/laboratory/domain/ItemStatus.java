package com.ngolahealth.backend.modules.laboratory.domain;

/**
 * PENDING → RESULTED (editável pelo laboratório) → VALIDATED (definitivo; corrige-se com motivo).
 */
public enum ItemStatus {
  PENDING,
  RESULTED,
  VALIDATED,
  CANCELLED
}
