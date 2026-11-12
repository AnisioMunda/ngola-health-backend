package com.ngolahealth.backend.modules.episodes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/** Sinais vitais medidos no episódio. Todos opcionais. */
@Embeddable
public record Vitals(
    @Column(name = "systolic_bp") Integer systolicBp,
    @Column(name = "diastolic_bp") Integer diastolicBp,
    @Column(name = "heart_rate") Integer heartRate,
    @Column(name = "respiratory_rate") Integer respiratoryRate,
    @Column(name = "temperature", precision = 4, scale = 1) BigDecimal temperature,
    @Column(name = "oxygen_saturation") Integer oxygenSaturation,
    @Column(name = "weight_kg", precision = 5, scale = 2) BigDecimal weightKg,
    @Column(name = "height_cm", precision = 5, scale = 1) BigDecimal heightCm) {

  public boolean isEmpty() {
    return systolicBp == null
        && diastolicBp == null
        && heartRate == null
        && respiratoryRate == null
        && temperature == null
        && oxygenSaturation == null
        && weightKg == null
        && heightCm == null;
  }
}
