package com.ngolahealth.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enquanto o utilizador tiver a troca de senha pendente, só pode consultar o perfil, trocar a senha
 * e terminar sessão. A regra é aplicada no backend, não apenas no frontend.
 */
@RequiredArgsConstructor
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

  static final Set<String> ALLOWED =
      Set.of("/auth/me", "/auth/change-password", "/auth/logout", "/auth/refresh", "/auth/login");

  private final ProblemJsonSecurityHandler problemWriter;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.getPrincipal() instanceof CurrentUser user
        && user.mustChangePassword()
        && !ALLOWED.contains(pathWithinApplication(request))) {
      problemWriter.writePasswordChangeRequired(response);
      return;
    }
    chain.doFilter(request, response);
  }

  private static String pathWithinApplication(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String context = request.getContextPath();
    return context != null && !context.isEmpty() && uri.startsWith(context)
        ? uri.substring(context.length())
        : uri;
  }
}
