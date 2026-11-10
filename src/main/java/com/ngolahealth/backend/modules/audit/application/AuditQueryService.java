package com.ngolahealth.backend.modules.audit.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.audit.api.AuditDtos.AuditEventResponse;
import com.ngolahealth.backend.modules.audit.api.AuditDtos.AuditSearch;
import com.ngolahealth.backend.modules.audit.domain.AuditEvent;
import com.ngolahealth.backend.modules.audit.domain.AuditEventRepository;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consulta da auditoria. O SUPER_ADMIN vê tudo; ADMIN e MANAGER só os eventos do seu hospital (os
 * de nível da plataforma ficam-lhes ocultos).
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final AuditEventRepository repository;
  private final CurrentUserProvider currentUser;
  private final JsonMapper jsonMapper;

  @Transactional(readOnly = true)
  public PageResponse<AuditEventResponse> search(AuditSearch query, int page, int size) {
    if (query.from() != null && query.to() != null && !query.to().isAfter(query.from())) {
      throw new BadRequestException(
          "INVALID_PERIOD", "A data final tem de ser posterior à data inicial.");
    }
    var actor = currentUser.get();
    var result =
        repository.search(
            scope(actor, query.hospitalId()),
            query.action(),
            query.entityType(),
            blankToNull(query.entityId()),
            query.actorId(),
            query.outcome(),
            query.from(),
            query.to(),
            likePattern(query.search()),
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.asc("id"))));
    return PageResponse.of(result, this::toResponse);
  }

  @Transactional(readOnly = true)
  public AuditEventResponse findById(UUID id) {
    var actor = currentUser.get();
    return repository
        .findById(id)
        .filter(e -> actor.isSuperAdmin() || Objects.equals(e.getHospitalId(), actor.hospitalId()))
        .map(this::toResponse)
        .orElseThrow(
            () -> new NotFoundException("AUDIT_EVENT_NOT_FOUND", "Evento não encontrado."));
  }

  private static UUID scope(CurrentUser actor, UUID requested) {
    return actor.isSuperAdmin() ? requested : actor.hospitalId();
  }

  private AuditEventResponse toResponse(AuditEvent e) {
    return new AuditEventResponse(
        e.getId(),
        e.getOccurredAt(),
        e.getHospitalId(),
        e.getActorId(),
        e.getActorUsername(),
        e.getActorName(),
        e.getAction(),
        e.getEntityType(),
        e.getEntityId(),
        e.getSummary(),
        e.getChanges() == null ? null : jsonMapper.readValue(e.getChanges(), MAP),
        e.getOutcome(),
        e.getIpAddress(),
        e.getUserAgent(),
        e.getRequestMethod(),
        e.getRequestPath(),
        e.getRequestId());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String likePattern(String search) {
    var term = blankToNull(search);
    if (term == null) {
      return null;
    }
    return "%"
        + term.toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        + "%";
  }
}
