package com.ngolahealth.backend.common.exception;

import org.springframework.http.HttpStatus;

/** O utilizador está autenticado mas a regra de negócio não lhe permite a operação. */
public class ForbiddenException extends BusinessException {

  public ForbiddenException(String code, String message) {
    super(HttpStatus.FORBIDDEN, code, message);
  }
}
