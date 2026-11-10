package com.ngolahealth.backend.common.exception;

import org.springframework.http.HttpStatus;

/** O recurso pedido não existe (ou não pertence ao hospital do utilizador). */
public class NotFoundException extends BusinessException {

  public NotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }

  public NotFoundException(String code, String message) {
    super(HttpStatus.NOT_FOUND, code, message);
  }
}
