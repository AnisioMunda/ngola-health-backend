package com.ngolahealth.backend.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Autenticação (prefixo {@code app.auth}). Validadas no arranque.
 *
 * @param jwtSecret segredo HS256 em Base64, com pelo menos 256 bits
 * @param issuer emissor gravado nos tokens e verificado na validação
 * @param accessTokenTtl validade do token de acesso (curta: a revogação é verificada por sessão)
 * @param refreshTokenTtl validade do refresh token, renovada a cada utilização
 * @param maxFailedAttempts tentativas falhadas antes do bloqueio temporário
 * @param lockDuration duração do bloqueio temporário
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    @NotBlank(message = "defina JWT_SECRET no .env (openssl rand -base64 32)") String jwtSecret,
    @NotBlank String issuer,
    @NotNull Duration accessTokenTtl,
    @NotNull Duration refreshTokenTtl,
    @Min(1) int maxFailedAttempts,
    @NotNull Duration lockDuration) {

  private static final int MIN_SECRET_BYTES = 32;

  public AuthProperties {
    if (jwtSecret != null && !jwtSecret.isBlank()) {
      byte[] key;
      try {
        key = Base64.getDecoder().decode(jwtSecret.strip());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("app.auth.jwt-secret (JWT_SECRET) não é Base64 válido");
      }
      if (key.length < MIN_SECRET_BYTES) {
        throw new IllegalArgumentException(
            "app.auth.jwt-secret (JWT_SECRET) deve ter pelo menos 256 bits");
      }
    }
  }

  public byte[] jwtSecretBytes() {
    return Base64.getDecoder().decode(jwtSecret.strip());
  }
}
