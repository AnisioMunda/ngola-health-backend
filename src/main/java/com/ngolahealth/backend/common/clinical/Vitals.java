package com.ngolahealth.backend.common.clinical;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/** Sinais vitais (triagem, episódios…). Todos opcionais; vazio = sem registo. */
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

  /** A TA diastólica tem de ser inferior à sistólica, quando ambas existem. */
  public void validate() {
    if (systolicBp != null && diastolicBp != null && diastolicBp >= systolicBp) {
      throw new com.ngolahealth.backend.common.exception.BadRequestException(
          "INVALID_BLOOD_PRESSURE", "A TA diastólica tem de ser inferior à sistólica.");
    }
  }

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
