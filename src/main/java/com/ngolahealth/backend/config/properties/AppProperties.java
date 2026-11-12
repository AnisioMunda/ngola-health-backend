package com.ngolahealth.backend.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades gerais da aplicação (prefixo {@code app}). Validadas no arranque: uma configuração
 * inválida impede a aplicação de iniciar.
 *
 * @param baseUrl origem do frontend, usada no CORS e em links enviados aos utilizadores
 * @param timeZone fuso horário local que define o "dia" (senhas, validades, relatórios)
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @NotBlank @Pattern(regexp = "https?://\\S+", message = "deve ser um URL HTTP(S) absoluto")
        String baseUrl,
    @NotNull ZoneId timeZone) {}
