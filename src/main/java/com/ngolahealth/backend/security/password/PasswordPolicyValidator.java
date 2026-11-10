package com.ngolahealth.backend.security.password;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

  static final int MIN_LENGTH = 10;
  static final int MAX_LENGTH = 64;
  static final int MAX_BCRYPT_BYTES = 72;

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return isStrong(value);
  }

  public static boolean isStrong(String value) {
    int length = value.codePointCount(0, value.length());
    return length >= MIN_LENGTH
        && length <= MAX_LENGTH
        && value.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_BYTES
        && value.codePoints().anyMatch(Character::isLowerCase)
        && value.codePoints().anyMatch(Character::isUpperCase)
        && value.codePoints().anyMatch(Character::isDigit)
        && value.strip().equals(value);
  }
}
