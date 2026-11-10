package com.ngolahealth.backend.modules.audit.application;

import com.ngolahealth.backend.common.web.RequestIdFilter;
import com.ngolahealth.backend.modules.audit.domain.AuditEvent;
import com.ngolahealth.backend.modules.audit.domain.AuditEventRepository;
import com.ngolahealth.backend.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ponto único de registo de auditoria para todos os módulos.
 *
 * <p>Corre na transacção de quem chama: se a alteração de negócio for revertida, o evento também é
 * (e vice-versa) — nunca fica uma alteração sem registo. Sem transacção activa (ex.: login
 * falhado), abre a sua própria.
 */
@Service
@RequiredArgsConstructor
public class AuditTrail {

  /** Atributo do pedido que indica que já houve registo explícito (ver rede de segurança). */
  public static final String RECORDED_ATTRIBUTE = AuditTrail.class.getName() + ".recorded";

  /** Campos técnicos que não interessam no histórico de alterações. */
  private static final Set<String> IGNORED_FIELDS = Set.of("version", "createdAt", "updatedAt");

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final AuditEventRepository repository;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  @Transactional
  public void record(AuditEntry entry) {
    var actor = entry.actor() != null ? entry.actor() : currentActor();
    var builder =
        AuditEvent.builder()
            .occurredAt(clock.instant())
            .hospitalId(entry.hospitalId())
            .action(entry.action())
            .entityType(entry.entityType())
            .entityId(entry.entityId())
            .summary(entry.summary())
            .changes(
                entry.changes() == null || entry.changes().isEmpty()
                    ? null
                    : jsonMapper.writeValueAsString(entry.changes()))
            .outcome(entry.outcome());
    if (actor != null) {
      builder.actorId(actor.id()).actorUsername(actor.username()).actorName(actor.fullName());
    }
    var request = currentRequest();
    if (request != null) {
      builder
          .ipAddress(request.getRemoteAddr())
          .userAgent(request.getHeader(HttpHeaders.USER_AGENT))
          .requestMethod(request.getMethod())
          .requestPath(request.getRequestURI())
          .requestId((String) request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }
    repository.save(builder.build());
    markHandled();
  }

  /**
   * Indica que o pedido foi tratado sem nada a registar (ex.: activar algo já activo), para a rede
   * de segurança não criar um evento genérico.
   */
  public void markHandled() {
    var attributes = RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      attributes.setAttribute(RECORDED_ATTRIBUTE, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
    }
  }

  /** Dados iniciais de um registo criado (campos nulos e técnicos omitidos). */
  public Map<String, Object> snapshot(Object created) {
    var result = new LinkedHashMap<String, Object>();
    toMap(created)
        .forEach(
            (field, value) -> {
              if (value != null && !IGNORED_FIELDS.contains(field) && !"id".equals(field)) {
                result.put(field, value);
              }
            });
    return result;
  }

  /** Campos que mudaram entre duas versões: {campo: {from, to}}. */
  public Map<String, Object> diff(Object before, Object after) {
    var old = toMap(before);
    var current = toMap(after);
    var result = new LinkedHashMap<String, Object>();
    for (var field : union(old.keySet(), current.keySet())) {
      if (IGNORED_FIELDS.contains(field)) {
        continue;
      }
      var from = old.get(field);
      var to = current.get(field);
      if (!Objects.equals(from, to)) {
        var change = new LinkedHashMap<String, Object>();
        change.put("from", from);
        change.put("to", to);
        result.put(field, change);
      }
    }
    return result;
  }

  private Map<String, Object> toMap(Object value) {
    return value == null ? Map.of() : jsonMapper.convertValue(value, MAP);
  }

  private static Collection<String> union(Set<String> a, Set<String> b) {
    var all = new java.util.LinkedHashSet<>(a);
    all.addAll(b);
    return all;
  }

  private static AuditActor currentActor() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return null;
    }
    if (auth.getPrincipal() instanceof CurrentUser user) {
      return new AuditActor(user.id(), user.username(), user.fullName());
    }
    return new AuditActor(null, auth.getName(), null);
  }

  private static HttpServletRequest currentRequest() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra
        ? sra.getRequest()
        : null;
  }
}
