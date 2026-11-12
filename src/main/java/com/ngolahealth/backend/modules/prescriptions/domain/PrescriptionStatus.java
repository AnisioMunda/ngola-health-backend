package com.ngolahealth.backend.modules.prescriptions.domain;

/** Estado gravado. "Expirada" não é gravada: calcula-se pela validade (ver Prescription). */
public enum PrescriptionStatus {
  ACTIVE,
  PARTIALLY_DISPENSED,
  DISPENSED,
  CANCELLED;

  public boolean isOpen() {
    return this == ACTIVE || this == PARTIALLY_DISPENSED;
  }
}
