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
      body(
          401,
          "Não autenticado",
          "É necessário iniciar sessão para aceder a este recurso.",
          "UNAUTHENTICATED");

  private static final String FORBIDDEN_BODY =
      body(
          403, "Acesso proibido", "Não tem permissão para aceder a este recurso.", "ACCESS_DENIED");

  private static final String PASSWORD_CHANGE_BODY =
      body(
          403,
          "Acesso proibido",
          "Tem de alterar a senha antes de continuar.",
          "PASSWORD_CHANGE_REQUIRED");

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

  void writePasswordChangeRequired(HttpServletResponse response) throws IOException {
    write(response, HttpStatus.FORBIDDEN, PASSWORD_CHANGE_BODY);
  }

  /** Os textos são constantes internas, sem aspas nem caracteres a escapar. */
  private static String body(int status, String title, String detail, String code) {
    return """
        {"type":"about:blank","title":"%s","status":%d,"detail":"%s","code":"%s"}"""
        .formatted(title, status, detail, code);
  }

  private static void write(HttpServletResponse response, HttpStatus status, String body)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(body);
  }
}
