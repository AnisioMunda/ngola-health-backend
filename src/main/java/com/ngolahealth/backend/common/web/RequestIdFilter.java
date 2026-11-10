package com.ngolahealth.backend.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Atribui um identificador a cada pedido: devolvido no cabeçalho {@code X-Request-Id}, incluído nos
 * logs (MDC {@code requestId}) e nos eventos de auditoria. Um id recebido do cliente só é aceite se
 * for um UUID (evita injecção de texto nos logs).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";
  private static final String MDC_KEY = "requestId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = validOrNew(request.getHeader(HEADER));
    request.setAttribute(ATTRIBUTE, id);
    response.setHeader(HEADER, id);
    MDC.put(MDC_KEY, id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  private static String validOrNew(String candidate) {
    if (candidate != null) {
      try {
        return UUID.fromString(candidate.strip()).toString();
      } catch (IllegalArgumentException ignored) {
        // gera um novo
      }
    }
    return UUID.randomUUID().toString();
  }
}
