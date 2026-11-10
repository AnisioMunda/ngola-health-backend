package com.ngolahealth.backend.common.exception;

import org.springframework.http.HttpStatus;

/** O pedido entra em conflito com o estado actual (duplicados, transições de estado inválidas). */
public class ConflictException extends BusinessException {

  public ConflictException(String code, String message) {
    super(HttpStatus.CONFLICT, code, message);
  }
}
