package com.ngolahealth.backend.modules.episodes.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.AssignDoctorRequest;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.CancelRequest;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.ClinicalNotesRequest;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.CreateEpisodeRequest;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.EpisodeResponse;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.EpisodeSummary;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.RescheduleRequest;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.VitalsRequest;
import com.ngolahealth.backend.modules.episodes.application.EpisodeService;
import com.ngolahealth.backend.modules.episodes.application.EpisodeService.EpisodeSearch;
import com.ngolahealth.backend.modules.episodes.domain.EpisodeStatus;
import com.ngolahealth.backend.modules.episodes.domain.EpisodeType;
import com.ngolahealth.backend.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/episodes")
@RequiredArgsConstructor
@Tag(name = "Episódios", description = "Consultas, urgências e outros episódios clínicos")
public class EpisodeController {

  private final EpisodeService service;

  @GetMapping
  @PreAuthorize(Authorities.PATIENT_READERS)
  @Operation(summary = "Pesquisar episódios (lista de trabalho ou histórico de um paciente)")
  public PageResponse<EpisodeSummary> search(
      @RequestParam(required = false) Set<EpisodeStatus> status,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) EpisodeType type,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "desc") String order,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 100, message = "O tamanho da página não pode exceder 100.")
          int size) {
    return service.search(
        new EpisodeSearch(
            status, patientId, doctorId, type, from, to, !"asc".equalsIgnoreCase(order)),
        page,
        size);
  }

  @GetMapping("/{id}")
  @PreAuthorize(Authorities.PATIENT_READERS)
  @Operation(summary = "Abrir episódio (a consulta fica na auditoria)")
  public EpisodeResponse view(@PathVariable UUID id) {
    return service.view(id);
  }

  @PostMapping
  @PreAuthorize(Authorities.EPISODE_SCHEDULERS)
  @Operation(summary = "Abrir/agendar episódio")
  public ResponseEntity<EpisodeResponse> create(@Valid @RequestBody CreateEpisodeRequest request) {
    var created = service.create(request);
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PutMapping("/{id}/schedule")
  @PreAuthorize(Authorities.EPISODE_SCHEDULERS)
  @Operation(summary = "Reagendar (só antes de iniciar)")
  public EpisodeResponse reschedule(
      @PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
    return service.reschedule(id, request);
  }

  @PutMapping("/{id}/doctor")
  @PreAuthorize(Authorities.EPISODE_SCHEDULERS)
  @Operation(summary = "Alterar médico responsável (até o episódio fechar)")
  public EpisodeResponse assignDoctor(
      @PathVariable UUID id, @RequestBody AssignDoctorRequest request) {
    return service.assignDoctor(id, request.doctorId());
  }

  @PatchMapping("/{id}/start")
  @PreAuthorize(Authorities.EPISODE_CARE)
  @Operation(summary = "Iniciar (agendado → em curso)")
  public EpisodeResponse start(@PathVariable UUID id) {
    return service.start(id);
  }

  @PutMapping("/{id}/vitals")
  @PreAuthorize(Authorities.EPISODE_CARE)
  @Operation(summary = "Registar sinais vitais (episódio em curso)")
  public EpisodeResponse vitals(@PathVariable UUID id, @Valid @RequestBody VitalsRequest request) {
    return service.recordVitals(id, request);
  }

  @PutMapping("/{id}/clinical-notes")
  @PreAuthorize(Authorities.EPISODE_DOCTORS)
  @Operation(summary = "Registar anamnese, diagnóstico e notas (episódio em curso)")
  public EpisodeResponse clinicalNotes(
      @PathVariable UUID id, @Valid @RequestBody ClinicalNotesRequest request) {
    return service.recordClinicalNotes(id, request);
  }

  @PatchMapping("/{id}/complete")
  @PreAuthorize(Authorities.EPISODE_DOCTORS)
  @Operation(summary = "Concluir (exige diagnóstico, excepto em exames)")
  public EpisodeResponse complete(@PathVariable UUID id) {
    return service.complete(id);
  }

  @PatchMapping("/{id}/cancel")
  @PreAuthorize(Authorities.EPISODE_SCHEDULERS)
  @Operation(summary = "Cancelar com motivo (em curso: só médico)")
  public EpisodeResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
    return service.cancel(id, request.reason());
  }
}
