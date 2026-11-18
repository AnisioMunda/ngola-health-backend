package com.ngolahealth.backend.common.web;

import com.ngolahealth.backend.security.UserLogContextFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Uma linha de log por pedido: método, caminho, estado e duração. A query string fica de fora
 * porque as pesquisas levam nomes e números de documento dos doentes. Os endpoints de gestão
 * ({@code /actuator}) e os pedidos CORS de pré-verificação não são registados.
 *
 * <p>Logger {@code ngolahealth.http}: 5xx em WARN, o resto em INFO.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger("ngolahealth.http");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    boolean failed = false;
    try {
      chain.doFilter(request, response);
    } catch (IOException | ServletException | RuntimeException e) {
      failed = true;
      throw e;
    } finally {
      try {
        if (!skip(request)) {
          int status = failed ? 500 : response.getStatus();
          long millis = (System.nanoTime() - start) / 1_000_000;
          var event = status >= 500 ? log.atWarn() : log.atInfo();
          event
              .addKeyValue("http.method", request.getMethod())
              .addKeyValue("http.path", request.getRequestURI())
              .addKeyValue("http.status", status)
              .addKeyValue("http.durationMs", millis)
              .log("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, millis);
        }
      } finally {
        MDC.remove(UserLogContextFilter.USER_ID);
        MDC.remove(UserLogContextFilter.HOSPITAL_ID);
      }
    }
  }

  private static boolean skip(HttpServletRequest request) {
    if ("OPTIONS".equals(request.getMethod())) {
      return true;
    }
    String context = request.getContextPath() == null ? "" : request.getContextPath();
    return request.getRequestURI().startsWith(context + "/actuator");
  }
}
