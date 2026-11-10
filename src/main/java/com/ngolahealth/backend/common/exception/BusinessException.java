package com.ngolahealth.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base de todas as excepções de negócio. Cada módulo cria subclasses (ou usa as genéricas deste
 * pacote) em vez de registar handlers próprios no {@link GlobalExceptionHandler}.
 *
 * <p>A mensagem é apresentada ao utilizador final, por isso deve estar em português e nunca conter
 * dados sensíveis.
 */
public abstract class BusinessException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  protected BusinessException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  /** Código estável, legível por máquina, para o frontend reagir a erros específicos. */
  public String getCode() {
    return code;
  }
}
