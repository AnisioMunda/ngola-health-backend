package com.ngolahealth.backend.modules.episodes.domain;

/** SCHEDULED → IN_PROGRESS → COMPLETED; SCHEDULED ou IN_PROGRESS → CANCELLED. */
public enum EpisodeStatus {
  SCHEDULED,
  IN_PROGRESS,
  COMPLETED,
  CANCELLED;

  public boolean isClosed() {
    return this == COMPLETED || this == CANCELLED;
  }
}
