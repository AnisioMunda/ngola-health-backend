package com.ngolahealth.backend.common.exception;

import org.springframework.http.HttpStatus;

/** Credenciais ou token inválidos. A mensagem nunca revela se a conta existe. */
public class UnauthorizedException extends BusinessException {

  public UnauthorizedException(String code, String message) {
    super(HttpStatus.UNAUTHORIZED, code, message);
  }
}
