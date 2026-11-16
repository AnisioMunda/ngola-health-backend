package com.ngolahealth.backend.modules.financial.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Regras de arredondamento dos valores em AOA: 2 casas, HALF_UP, linha a linha. */
public final class Money {

  public static final String CURRENCY = "AOA";
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private Money() {}

  public static BigDecimal round(BigDecimal amount) {
    return Objects.requireNonNull(amount).setScale(2, RoundingMode.HALF_UP);
  }

  /** {@code amount × percent / 100}, arredondado. */
  public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
    return round(amount.multiply(percent).divide(HUNDRED, 10, RoundingMode.HALF_UP));
  }

  public static BigDecimal zero() {
    return BigDecimal.ZERO.setScale(2);
  }
}
