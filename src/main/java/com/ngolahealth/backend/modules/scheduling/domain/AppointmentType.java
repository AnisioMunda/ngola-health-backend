package com.ngolahealth.backend.modules.scheduling.domain;

import com.ngolahealth.backend.modules.episodes.domain.EpisodeType;

/** Tipo de marcação. Urgências não se marcam: entram pela triagem. */
public enum AppointmentType {
  CONSULTATION(EpisodeType.OUTPATIENT),
  FOLLOW_UP(EpisodeType.OUTPATIENT),
  EXAM(EpisodeType.EXAM);

  private final EpisodeType episodeType;

  AppointmentType(EpisodeType episodeType) {
    this.episodeType = episodeType;
  }

  /** Tipo do episódio aberto quando o paciente chega. */
  public EpisodeType episodeType() {
    return episodeType;
  }
}
