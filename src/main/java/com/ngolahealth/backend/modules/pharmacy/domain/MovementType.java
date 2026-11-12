package com.ngolahealth.backend.modules.pharmacy.domain;

/** Tipos de movimento de stock; o sinal da quantidade decorre do tipo. */
public enum MovementType {
  RECEIPT(true),
  DISPENSE(false),
  ADJUSTMENT_IN(true),
  ADJUSTMENT_OUT(false),
  WRITE_OFF_EXPIRED(false),
  WRITE_OFF_DAMAGED(false);

  private final boolean inbound;

  MovementType(boolean inbound) {
    this.inbound = inbound;
  }

  public boolean isInbound() {
    return inbound;
  }
}
