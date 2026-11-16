package com.ngolahealth.backend.modules.financial.domain;

import java.math.BigDecimal;

public record ServicePriceDetails(
    String code,
    String description,
    ServiceCategory category,
    BigDecimal unitPrice,
    BigDecimal vatRate,
    String vatExemptionReason) {}
