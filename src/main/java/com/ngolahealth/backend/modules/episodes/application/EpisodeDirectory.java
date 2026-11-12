package com.ngolahealth.backend.modules.episodes.application;

import com.ngolahealth.backend.modules.episodes.domain.EpisodeRepository;
import com.ngolahealth.backend.modules.episodes.domain.EpisodeStatus;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Acesso de outros módulos aos episódios (no âmbito do hospital do utilizador). */
@Service
@RequiredArgsConstructor
public class EpisodeDirectory {

  private final EpisodeRepository episodes;

  public record EpisodeRef(UUID id, UUID patientId, EpisodeStatus status) {}

  @Transactional(readOnly = true)
  public Optional<EpisodeRef> find(UUID id) {
    return episodes
        .findScoped(id)
        .map(e -> new EpisodeRef(e.getId(), e.getPatientId(), e.getStatus()));
  }
}
