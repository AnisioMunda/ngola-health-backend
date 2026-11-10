package com.ngolahealth.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Responde a falhas de autenticação (401) e autorização (403) detectadas nos filtros de segurança
 * com o mesmo formato problem+json usado pelo {@code GlobalExceptionHandler}, para que o frontend
 * trate todos os erros da mesma maneira.
 */
@Component
public class ProblemJsonSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private static final String UNAUTHORIZED_BODY =
      """
      {"type":"about:blank","title":"Não autenticado","status":401,\
      "detail":"É necessário iniciar sessão para aceder a este recurso.","code":"UNAUTHENTICATED"}""";

  private static final String FORBIDDEN_BODY =
      """
      {"type":"about:blank","title":"Acesso proibido","status":403,\
      "detail":"Não tem permissão para aceder a este recurso.","code":"ACCESS_DENIED"}""";

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    write(response, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_BODY);
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    write(response, HttpStatus.FORBIDDEN, FORBIDDEN_BODY);
  }

  private static void write(HttpServletResponse response, HttpStatus status, String body)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(body);
  }
}
