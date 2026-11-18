package com.ngolahealth.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Separa as duas APIs: um token do portal só serve em {@code /portal/**} e nas rotas partilhadas
 * (sessão e notificações); um token do pessoal não entra no portal. A verificação é feita aqui, à
 * entrada, e não depende de cada endpoint se lembrar dela.
 */
@RequiredArgsConstructor
public class PortalAudienceFilter extends OncePerRequestFilter {

  static final String PORTAL_PREFIX = "/portal/";

  /** Rotas que ambos os tipos de conta usam. */
  static final List<String> SHARED_PREFIXES = List.of("/auth/", "/notifications");

  private final ProblemJsonSecurityHandler problemWriter;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
      String path = pathWithinApplication(request);
      boolean shared = SHARED_PREFIXES.stream().anyMatch(path::startsWith);
      boolean portalPath = path.startsWith(PORTAL_PREFIX);
      if (!shared && user.isPortal() != portalPath) {
        problemWriter.handle(
            request,
            response,
            new AccessDeniedException(
                user.isPortal()
                    ? "Conta do portal: área reservada ao pessoal."
                    : "Área do portal do paciente."));
        return;
      }
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
