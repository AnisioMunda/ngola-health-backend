package com.ngolahealth.backend.common.exception;

import org.springframework.http.HttpStatus;

/** Pedido sintacticamente válido mas que viola uma regra de negócio. */
public class BadRequestException extends BusinessException {

  public BadRequestException(String code, String message) {
    super(HttpStatus.BAD_REQUEST, code, message);
  }
}
