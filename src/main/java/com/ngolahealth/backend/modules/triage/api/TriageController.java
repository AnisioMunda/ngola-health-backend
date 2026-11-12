package com.ngolahealth.backend.modules.triage.api;

import com.ngolahealth.backend.modules.triage.api.TriageDtos.ChangePriorityRequest;
import com.ngolahealth.backend.modules.triage.api.TriageDtos.CreateTriageRequest;
import com.ngolahealth.backend.modules.triage.api.TriageDtos.IdentifyRequest;
import com.ngolahealth.backend.modules.triage.api.TriageDtos.OpenEpisodeRequest;
import com.ngolahealth.backend.modules.triage.api.TriageDtos.TriageResponse;
import com.ngolahealth.backend.modules.triage.api.TriageDtos.TriageStats;
import com.ngolahealth.backend.modules.triage.application.TriageService;
import com.ngolahealth.backend.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/triage")
@RequiredArgsConstructor
@Tag(name = "Triagem", description = "Triagem de Manchester e fila de urgência")
public class TriageController {

  private final TriageService service;

  @GetMapping("/queue")
  @PreAuthorize(Authorities.PATIENT_READERS)
  @Operation(summary = "Fila activa: em espera (por prioridade e chegada) e em atendimento")
  public List<TriageResponse> queue() {
    return service.activeQueue();
  }

  @GetMapping("/stats")
  @PreAuthorize(Authorities.PATIENT_READERS)
  @Operation(summary = "Indicadores do dia")
  public TriageStats stats() {
    return service.stats();
  }

  @GetMapping
  @PreAuthorize(Authorities.PATIENT_READERS)
  @Operation(summary = "Triagens de um dia (por omissão, hoje)")
  public List<TriageResponse> day(@RequestParam(required = false) LocalDate day) {
    return service.day(day);
  }

  @GetMapping("/{id}")
  @PreAuthorize(Authorities.PATIENT_READERS)
  @Operation(summary = "Abrir triagem (com histórico de re-triagens)")
  public TriageResponse view(@PathVariable UUID id) {
    return service.view(id);
  }

  @PostMapping
  @PreAuthorize(Authorities.TRIAGE_STAFF)
  @Operation(summary = "Registar triagem (atribui a senha do dia)")
  public ResponseEntity<TriageResponse> create(@Valid @RequestBody CreateTriageRequest request) {
    var created = service.create(request);
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PatchMapping("/{id}/priority")
  @PreAuthorize(Authorities.TRIAGE_STAFF)
  @Operation(summary = "Re-triagem (só em espera; motivo obrigatório)")
  public TriageResponse changePriority(
      @PathVariable UUID id, @Valid @RequestBody ChangePriorityRequest request) {
    return service.changePriority(id, request);
  }

  @PatchMapping("/{id}/patient")
  @PreAuthorize(Authorities.TRIAGE_STAFF)
  @Operation(summary = "Identificar doente provisório (associar ao paciente registado)")
  public TriageResponse identify(
      @PathVariable UUID id, @Valid @RequestBody IdentifyRequest request) {
    return service.identify(id, request.patientId());
  }

  @PatchMapping("/{id}/call")
  @PreAuthorize(Authorities.TRIAGE_STAFF)
  @Operation(summary = "Chamar doente (em espera → em atendimento)")
  public TriageResponse call(@PathVariable UUID id) {
    return service.call(id);
  }

  @PostMapping("/{id}/episode")
  @PreAuthorize(Authorities.TRIAGE_STAFF)
  @Operation(summary = "Abrir episódio de urgência e concluir a triagem")
  public TriageResponse openEpisode(
      @PathVariable UUID id, @RequestBody(required = false) OpenEpisodeRequest request) {
    return service.openEpisode(id, request == null ? null : request.doctorId());
  }

  @PatchMapping("/{id}/complete")
  @PreAuthorize(Authorities.TRIAGE_STAFF)
  @Operation(summary = "Concluir sem episódio")
  public TriageResponse complete(@PathVariable UUID id) {
    return service.complete(id);
  }

  @PatchMapping("/{id}/left")
  @PreAuthorize(Authorities.TRIAGE_DESK)
  @Operation(summary = "Saiu sem ser atendido (só em espera)")
  public TriageResponse left(@PathVariable UUID id) {
    return service.markLeft(id);
  }
}
