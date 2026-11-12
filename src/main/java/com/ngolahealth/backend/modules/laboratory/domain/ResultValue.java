package com.ngolahealth.backend.modules.laboratory.domain;

import java.math.BigDecimal;

/** Resultado introduzido: numérico ou texto (qualitativo, com indicação de anormal). */
public record ResultValue(
    BigDecimal numericValue, String textValue, boolean abnormal, String notes) {}
