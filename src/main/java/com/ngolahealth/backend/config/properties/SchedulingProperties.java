package com.ngolahealth.backend.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Agendamento (prefixo {@code app.scheduling}).
 *
 * @param maxDaysAhead até quantos dias no futuro se pode marcar
 * @param reminderCron quando os médicos recebem o resumo da agenda do dia seguinte (fuso {@code
 *     app.time-zone})
 */
@Validated
@ConfigurationProperties(prefix = "app.scheduling")
public record SchedulingProperties(
    @Min(1) @Max(730) int maxDaysAhead, @NotBlank String reminderCron) {}
