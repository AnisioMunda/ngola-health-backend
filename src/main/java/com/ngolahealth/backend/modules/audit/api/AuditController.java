package com.ngolahealth.backend.modules.audit.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.audit.api.AuditDtos.AuditEventResponse;
import com.ngolahealth.backend.modules.audit.api.AuditDtos.AuditSearch;
import com.ngolahealth.backend.modules.audit.application.AuditQueryService;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.audit.domain.AuditOutcome;
import com.ngolahealth.backend.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta (só leitura) da auditoria. Não existem endpoints de alteração ou remoção. */
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@PreAuthorize(Authorities.AUDIT_READERS)
@Tag(name = "Auditoria", description = "Consulta do registo de acções")
public class AuditController {

  private final AuditQueryService service;

  @GetMapping
  @Operation(summary = "Pesquisar eventos (mais recentes primeiro)")
  public PageResponse<AuditEventResponse> search(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) AuditAction action,
      @RequestParam(required = false) AuditEntityType entityType,
      @RequestParam(required = false) String entityId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) AuditOutcome outcome,
      @RequestParam(required = false) UUID hospitalId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "50")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 200, message = "O tamanho da página não pode exceder 200.")
          int size) {
    return service.search(
        new AuditSearch(
            search, action, entityType, entityId, actorId, outcome, hospitalId, from, to),
        page,
        size);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Obter evento")
  public AuditEventResponse findById(@PathVariable UUID id) {
    return service.findById(id);
  }
}
