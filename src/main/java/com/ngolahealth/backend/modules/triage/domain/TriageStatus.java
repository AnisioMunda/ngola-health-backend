package com.ngolahealth.backend.modules.triage.domain;

/** WAITING → IN_PROGRESS → COMPLETED; WAITING → LEFT (saiu sem ser atendido). */
public enum TriageStatus {
  WAITING,
  IN_PROGRESS,
  COMPLETED,
  LEFT;

  public boolean isClosed() {
    return this == COMPLETED || this == LEFT;
  }
}
