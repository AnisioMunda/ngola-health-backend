package com.ngolahealth.backend.modules.pharmacy.domain;

import java.util.UUID;

/** Contexto de um movimento: para quem, em que episódio, por que documento e porquê. */
public record MovementContext(UUID patientId, UUID episodeId, UUID referenceId, String reason) {

  public static MovementContext reason(String reason) {
    return new MovementContext(null, null, null, reason);
  }
}
