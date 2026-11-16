package com.ngolahealth.backend.modules.financial.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Uma linha a facturar, já validada (preço do catálogo ou linha livre). */
public record LineInput(
    UUID servicePriceId,
    String code,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal discountPercent,
    BigDecimal vatRate,
    String vatExemptionReason) {}
