package com.ngolahealth.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Acesso do Prometheus a {@code /actuator/prometheus} (prefixo {@code app.metrics}). O Prometheus
 * não consegue usar os tokens de sessão (expiram em minutos), por isso tem credenciais próprias,
 * enviadas por HTTP Basic. Sem utilizador ou senha o endpoint fica fechado.
 *
 * @param username utilizador do Prometheus
 * @param password senha do Prometheus (mínimo 16 caracteres)
 */
@ConfigurationProperties(prefix = "app.metrics")
public record MetricsProperties(String username, String password) {

  static final int MIN_PASSWORD_LENGTH = 16;

  public MetricsProperties {
    if (password != null && !password.isEmpty() && password.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "app.metrics.password (METRICS_PASSWORD) deve ter pelo menos "
              + MIN_PASSWORD_LENGTH
              + " caracteres.");
    }
  }

  public boolean enabled() {
    return username != null && !username.isBlank() && password != null && !password.isEmpty();
  }
}
