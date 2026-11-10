package com.ngolahealth.backend.modules.audit.application;

import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.audit.domain.AuditOutcome;
import com.ngolahealth.backend.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rede de segurança transversal da auditoria:
 *
 * <ul>
 *   <li>acesso negado (403) a qualquer endpoint fica registado;
 *   <li>uma escrita bem-sucedida sem evento explícito fica registada de forma genérica e gera um
 *       aviso no log — sinal de que o serviço deve passar a registar um evento próprio.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditSafetyNetInterceptor implements HandlerInterceptor {

  private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  /** Escritas sem interesse para auditoria (a renovação de sessão não altera dados). */
  private static final Set<String> IGNORED_PATHS = Set.of("/auth/refresh", "/auth/login");

  private final AuditTrail trail;
  private final CurrentUserProvider currentUser;

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    if (!(handler instanceof HandlerMethod) || isRecorded(request)) {
      return;
    }
    String path = pathWithinApplication(request);
    if (IGNORED_PATHS.contains(path)) {
      return;
    }
    int status = response.getStatus();
    try {
      if (status == HttpStatus.FORBIDDEN.value()) {
        trail.record(
            entry(
                AuditAction.ACCESS_DENIED,
                "Acesso negado: " + request.getMethod() + " " + path,
                AuditOutcome.DENIED));
      } else if (WRITE_METHODS.contains(request.getMethod()) && status >= 200 && status < 300) {
        log.warn(
            "Escrita sem evento de auditoria explícito: {} {} (registo genérico)",
            request.getMethod(),
            path);
        trail.record(
            entry(
                AuditAction.REQUEST,
                "Pedido " + request.getMethod() + " " + path + " concluído (" + status + ")",
                AuditOutcome.SUCCESS));
      }
    } catch (RuntimeException e) {
      // A resposta já foi enviada: uma falha aqui não pode afectar o utilizador.
      log.error("Falha ao registar evento de auditoria da rede de segurança", e);
    }
  }

  private AuditEntry entry(AuditAction action, String summary, AuditOutcome outcome) {
    UUID hospitalId = currentUser.find().map(u -> u.hospitalId()).orElse(null);
    return AuditEntry.success(action, AuditEntityType.SYSTEM, null, hospitalId, summary)
        .withOutcome(outcome);
  }

  private static boolean isRecorded(HttpServletRequest request) {
    return Boolean.TRUE.equals(request.getAttribute(AuditTrail.RECORDED_ATTRIBUTE));
  }

  private static String pathWithinApplication(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String context = request.getContextPath();
    return context != null && !context.isEmpty() && uri.startsWith(context)
        ? uri.substring(context.length())
        : uri;
  }
}
