package com.ngolahealth.backend.common.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converte excepções em respostas RFC 9457 (application/problem+json), sempre em português e sem
 * expor detalhes internos. Todas as respostas de erro têm a propriedade {@code code}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ProblemDetail handleBusiness(BusinessException ex) {
    return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new TreeMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(
            error ->
                errors.putIfAbsent(
                    error.getField(),
                    error.getDefaultMessage() != null
                        ? error.getDefaultMessage()
                        : "Valor inválido."));
    var problem =
        problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Um ou mais campos são inválidos.");
    problem.setProperty("errors", errors);
    return problem;
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
    Map<String, String> errors = new TreeMap<>();
    ex.getConstraintViolations()
        .forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
    var problem =
        problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Um ou mais parâmetros são inválidos.");
    problem.setProperty("errors", errors);
    return problem;
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingRequestHeaderException.class,
    MissingServletRequestParameterException.class
  })
  public ProblemDetail handleMalformed(Exception ex) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "MALFORMED_REQUEST",
        "Os dados enviados não têm um formato válido.");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail handleNoResource(NoResourceFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "O recurso solicitado não foi encontrado.");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
    return problem(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "O método HTTP não é permitido para este recurso.");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ProblemDetail handleMediaType(HttpMediaTypeNotSupportedException ex) {
    return problem(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "O formato do conteúdo enviado não é suportado.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
    log.warn("Violação de integridade de dados: {}", ex.getMostSpecificCause().getMessage());
    return problem(
        HttpStatus.CONFLICT,
        "DATA_CONFLICT",
        "Os dados informados entram em conflito com um registo existente.");
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "CONCURRENT_MODIFICATION",
        "O registo foi alterado por outro utilizador. Actualize e tente novamente.");
  }

  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthentication(AuthenticationException ex) {
    return problem(
        HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "As credenciais informadas são inválidas.");
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    return problem(
        HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Não tem permissão para aceder a este recurso.");
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
    String detail =
        ex.getReason() == null || ex.getReason().isBlank()
            ? "O pedido não pôde ser processado."
            : ex.getReason();
    return problem(ex.getStatusCode(), "REQUEST_FAILED", detail);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("Erro inesperado ao processar o pedido", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "Ocorreu um erro interno. Tente novamente mais tarde.");
  }

  static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(titleFor(status.value()));
    problem.setProperty("code", code);
    return problem;
  }

  static String titleFor(int status) {
    return switch (status) {
      case 400 -> "Pedido inválido";
      case 401 -> "Não autenticado";
      case 403 -> "Acesso proibido";
      case 404 -> "Recurso não encontrado";
      case 405 -> "Método não permitido";
      case 409 -> "Conflito";
      case 415 -> "Formato não suportado";
      case 500 -> "Erro interno";
      default -> "Pedido não processado";
    };
  }
}
