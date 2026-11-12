package com.ngolahealth.backend.config.properties;

import com.ngolahealth.backend.modules.triage.domain.TriagePriority;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Triagem (prefixo {@code app.triage}).
 *
 * @param zone fuso horário que define o "dia" da senha (Africa/Luanda)
 * @param targetMinutes tempo-alvo até ao atendimento, por prioridade; a direcção clínica de cada
 *     hospital deve validar estes valores
 */
@Validated
@ConfigurationProperties(prefix = "app.triage")
public record TriageProperties(
    @NotNull ZoneId zone, @NotEmpty Map<TriagePriority, Integer> targetMinutes) {

  public TriageProperties {
    if (targetMinutes != null) {
      for (var priority : TriagePriority.values()) {
        if (!targetMinutes.containsKey(priority)) {
          throw new IllegalArgumentException(
              "app.triage.target-minutes sem valor para " + priority);
        }
      }
    }
  }

  public int targetFor(TriagePriority priority) {
    return targetMinutes.get(priority);
  }
}
