package com.ngolahealth.backend.security.password;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Política de senha: 10 a 64 caracteres, com minúscula, maiúscula e dígito, e no máximo 72 bytes em
 * UTF-8 (limite do BCrypt). Null é tratado por {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

  String message() default
      "A senha deve ter 10 a 64 caracteres, com letras minúsculas, maiúsculas e números.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
