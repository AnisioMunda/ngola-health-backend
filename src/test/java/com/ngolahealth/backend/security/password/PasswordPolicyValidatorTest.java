package com.ngolahealth.backend.security.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyValidatorTest {

  @Test
  void acceptsStrongPassword() {
    assertThat(PasswordPolicyValidator.isStrong("Hospital2026")).isTrue();
    assertThat(PasswordPolicyValidator.isStrong("Saúde-Angola-1")).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Curta1A", // menos de 10 caracteres
        "semmaiusculas123", // sem maiúscula
        "SEMMINUSCULAS123", // sem minúscula
        "SemNumerosAqui", // sem dígito
        " Espaco-Inicial1", // espaços nas pontas
      })
  void rejectsWeakPasswords(String password) {
    assertThat(PasswordPolicyValidator.isStrong(password)).isFalse();
  }

  @Test
  void rejectsPasswordsOverBcryptByteLimit() {
    // 30 caracteres, mas 4 bytes cada em UTF-8 (> 72 bytes)
    String password = "Aa1" + "😀".repeat(27);
    assertThat(PasswordPolicyValidator.isStrong(password)).isFalse();
  }
}
