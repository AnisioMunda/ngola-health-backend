package com.ngolahealth.backend.modules.users.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Contador {@code ngolahealth.auth.logins} por resultado ({@code success}, {@code
 * invalid_credentials}, {@code account_locked}, …). Um pico de falhas é o primeiro sinal de um
 * ataque de força bruta.
 */
@Component
@RequiredArgsConstructor
public class LoginMetrics {

  public static final String METER = "ngolahealth.auth.logins";

  private final MeterRegistry registry;

  void success() {
    counter("success").increment();
  }

  void failure(String code) {
    counter(code == null ? "error" : code.toLowerCase(Locale.ROOT)).increment();
  }

  private Counter counter(String outcome) {
    return Counter.builder(METER)
        .description("Tentativas de login por resultado")
        .tag("outcome", outcome)
        .register(registry);
  }
}
