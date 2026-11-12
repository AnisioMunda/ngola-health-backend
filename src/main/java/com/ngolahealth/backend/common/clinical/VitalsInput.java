package com.ngolahealth.backend.common.clinical;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/** Sinais vitais recebidos pela API, com limites de plausibilidade (erros de digitação). */
public record VitalsInput(
    @Min(value = 40, message = "TA sistólica entre 40 e 300 mmHg.")
        @Max(value = 300, message = "TA sistólica entre 40 e 300 mmHg.")
        Integer systolicBp,
    @Min(value = 20, message = "TA diastólica entre 20 e 200 mmHg.")
        @Max(value = 200, message = "TA diastólica entre 20 e 200 mmHg.")
        Integer diastolicBp,
    @Min(value = 20, message = "Frequência cardíaca entre 20 e 250 bpm.")
        @Max(value = 250, message = "Frequência cardíaca entre 20 e 250 bpm.")
        Integer heartRate,
    @Min(value = 4, message = "Frequência respiratória entre 4 e 80 cpm.")
        @Max(value = 80, message = "Frequência respiratória entre 4 e 80 cpm.")
        Integer respiratoryRate,
    @DecimalMin(value = "30.0", message = "Temperatura entre 30,0 e 45,0 °C.")
        @DecimalMax(value = "45.0", message = "Temperatura entre 30,0 e 45,0 °C.")
        BigDecimal temperature,
    @Min(value = 50, message = "Saturação de O₂ entre 50 e 100 %.")
        @Max(value = 100, message = "Saturação de O₂ entre 50 e 100 %.")
        Integer oxygenSaturation,
    @DecimalMin(value = "0.3", message = "Peso entre 0,3 e 500 kg.")
        @DecimalMax(value = "500", message = "Peso entre 0,3 e 500 kg.")
        BigDecimal weightKg,
    @DecimalMin(value = "20", message = "Altura entre 20 e 250 cm.")
        @DecimalMax(value = "250", message = "Altura entre 20 e 250 cm.")
        BigDecimal heightCm) {

  public Vitals toVitals() {
    var vitals =
        new Vitals(
            systolicBp,
            diastolicBp,
            heartRate,
            respiratoryRate,
            temperature,
            oxygenSaturation,
            weightKg,
            heightCm);
    vitals.validate();
    return vitals.isEmpty() ? null : vitals;
  }
}
