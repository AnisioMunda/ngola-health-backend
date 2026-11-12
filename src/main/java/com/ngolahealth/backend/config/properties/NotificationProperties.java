package com.ngolahealth.backend.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Notificações (prefixo {@code app.notifications}).
 *
 * @param retention tempo que uma notificação é guardada (lida ou não) antes de ser apagada
 * @param cleanupCron quando corre a limpeza diária
 * @param stockAlertCron quando é gerado o resumo diário de stock (no fuso {@code app.time-zone})
 * @param stockExpiryDays lotes que expiram dentro deste número de dias entram no resumo
 */
@Validated
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(
    @NotNull Duration retention,
    @NotBlank String cleanupCron,
    @NotBlank String stockAlertCron,
    @Min(1) @Max(365) int stockExpiryDays) {}
