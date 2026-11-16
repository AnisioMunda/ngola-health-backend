package com.ngolahealth.backend.modules.scheduling.domain;

/**
 * SCHEDULED → CONFIRMED → CHECKED_IN (episódio aberto); SCHEDULED ou CONFIRMED → NO_SHOW ou
 * CANCELLED. Só SCHEDULED e CONFIRMED ocupam a vaga e podem mudar.
 */
public enum AppointmentStatus {
  SCHEDULED,
  CONFIRMED,
  CHECKED_IN,
  NO_SHOW,
  CANCELLED;

  public boolean isPending() {
    return this == SCHEDULED || this == CONFIRMED;
  }

  /** Ocupa lugar na vaga (as faltas e cancelamentos libertam-no). */
  public boolean occupiesSlot() {
    return this != NO_SHOW && this != CANCELLED;
  }
}
