package com.ngolahealth.backend.modules.laboratory.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.AmendRequest;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.CancelRequest;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.CollectRequest;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.CreateLabRequest;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.LabRequestResponse;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.LabTestRequest;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.LabTestResponse;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.ResultRequest;
import com.ngolahealth.backend.modules.laboratory.application.LabRequestService;
import com.ngolahealth.backend.modules.laboratory.application.LabTestService;
import com.ngolahealth.backend.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lab")
@RequiredArgsConstructor
@Tag(name = "Laboratório", description = "Catálogo de exames, pedidos e resultados")
public class LabController {

  private final LabTestService catalog;
  private final LabRequestService requests;

  @GetMapping("/tests")
  @PreAuthorize(Authorities.LAB_READERS)
  @Operation(summary = "Catálogo de exames")
  public List<LabTestResponse> tests(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active) {
    return catalog.search(search, active);
  }

  @PostMapping("/tests")
  @PreAuthorize(Authorities.LAB_CATALOG)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Adicionar exame ao catálogo")
  public LabTestResponse createTest(@Valid @RequestBody LabTestRequest request) {
    return catalog.create(request);
  }

  @PutMapping("/tests/{id}")
  @PreAuthorize(Authorities.LAB_CATALOG)
  @Operation(summary = "Editar exame (não altera pedidos já feitos)")
  public LabTestResponse updateTest(
      @PathVariable UUID id, @Valid @RequestBody LabTestRequest request) {
    return catalog.update(id, request);
  }

  @PatchMapping("/tests/{id}/activate")
  @PreAuthorize(Authorities.LAB_CATALOG)
  public LabTestResponse activateTest(@PathVariable UUID id) {
    return catalog.setActive(id, true);
  }

  @PatchMapping("/tests/{id}/deactivate")
  @PreAuthorize(Authorities.LAB_CATALOG)
  public LabTestResponse deactivateTest(@PathVariable UUID id) {
    return catalog.setActive(id, false);
  }

  @GetMapping("/requests")
  @PreAuthorize(Authorities.LAB_READERS)
  @Operation(summary = "Pedidos (open=true: lista de trabalho do laboratório; urgentes primeiro)")
  public PageResponse<LabRequestResponse> search(
      @RequestParam(defaultValue = "false") boolean open,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) String number,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 100, message = "O tamanho da página não pode exceder 100.")
          int size) {
    return requests.search(open, patientId, number, page, size);
  }

  @GetMapping("/requests/episode/{episodeId}")
  @PreAuthorize(Authorities.LAB_READERS)
  public List<LabRequestResponse> byEpisode(@PathVariable UUID episodeId) {
    return requests.byEpisode(episodeId);
  }

  @GetMapping("/requests/{id}")
  @PreAuthorize(Authorities.LAB_READERS)
  @Operation(summary = "Abrir pedido (fica na auditoria)")
  public LabRequestResponse view(@PathVariable UUID id) {
    return requests.view(id);
  }

  @PostMapping("/requests")
  @PreAuthorize(Authorities.LAB_REQUESTERS)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Pedir exames num episódio em curso")
  public LabRequestResponse create(@Valid @RequestBody CreateLabRequest request) {
    return requests.create(request);
  }

  @PostMapping("/requests/{id}/collect")
  @PreAuthorize(Authorities.SAMPLE_COLLECTORS)
  @Operation(summary = "Registar colheita da amostra")
  public LabRequestResponse collect(
      @PathVariable UUID id, @Valid @RequestBody(required = false) CollectRequest request) {
    return requests.collect(id, request == null ? null : request.notes());
  }

  @PostMapping("/requests/{id}/start")
  @PreAuthorize(Authorities.LAB_TECHNICIANS)
  @Operation(summary = "Iniciar análise")
  public LabRequestResponse start(@PathVariable UUID id) {
    return requests.startAnalysis(id);
  }

  @PutMapping("/requests/{id}/items/{itemId}/result")
  @PreAuthorize(Authorities.LAB_TECHNICIANS)
  @Operation(summary = "Registar ou rever resultado (antes de validado)")
  public LabRequestResponse result(
      @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody ResultRequest request) {
    return requests.recordResult(id, itemId, request);
  }

  @PostMapping("/requests/{id}/items/{itemId}/validate")
  @PreAuthorize(Authorities.LAB_TECHNICIANS)
  @Operation(summary = "Validar resultado (fica visível para os clínicos)")
  public LabRequestResponse validate(@PathVariable UUID id, @PathVariable UUID itemId) {
    return requests.validate(id, itemId);
  }

  @PostMapping("/requests/{id}/items/{itemId}/amend")
  @PreAuthorize(Authorities.LAB_TECHNICIANS)
  @Operation(summary = "Corrigir resultado validado (motivo obrigatório; guarda o valor anterior)")
  public LabRequestResponse amend(
      @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody AmendRequest request) {
    return requests.amend(id, itemId, request);
  }

  @PatchMapping("/requests/{id}/cancel")
  @PreAuthorize(Authorities.LAB_REQUESTERS)
  @Operation(summary = "Cancelar pedido (motivo obrigatório)")
  public LabRequestResponse cancel(
      @PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
    return requests.cancel(id, request.reason());
  }
}
