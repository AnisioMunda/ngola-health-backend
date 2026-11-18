package com.ngolahealth.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Junta aos logs (MDC) o utilizador e o hospital do pedido autenticado: só identificadores, nunca
 * nomes. As chaves são retiradas pelo {@link com.ngolahealth.backend.common.web.AccessLogFilter},
 * que ainda as usa na linha de acesso depois de a cadeia de segurança terminar.
 */
public class UserLogContextFilter extends OncePerRequestFilter {

  public static final String USER_ID = "userId";
  public static final String HOSPITAL_ID = "hospitalId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
      MDC.put(USER_ID, user.id().toString());
      if (user.hospitalId() != null) {
        MDC.put(HOSPITAL_ID, user.hospitalId().toString());
      }
    }
    chain.doFilter(request, response);
  }
}
