package com.ngolahealth.backend.modules.prescriptions.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.prescriptions.api.PrescriptionDtos.CancelRequest;
import com.ngolahealth.backend.modules.prescriptions.api.PrescriptionDtos.CreatePrescriptionRequest;
import com.ngolahealth.backend.modules.prescriptions.api.PrescriptionDtos.DispenseRequest;
import com.ngolahealth.backend.modules.prescriptions.api.PrescriptionDtos.PrescriptionResponse;
import com.ngolahealth.backend.modules.prescriptions.application.PrescriptionService;
import com.ngolahealth.backend.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/prescriptions")
@RequiredArgsConstructor
@Tag(name = "Prescrições", description = "Prescrição médica e dispensa pela farmácia")
public class PrescriptionController {

  private final PrescriptionService service;

  @GetMapping
  @PreAuthorize(Authorities.PRESCRIPTION_READERS)
  @Operation(summary = "Pesquisar (open=true: por dispensar; por paciente ou número)")
  public PageResponse<PrescriptionResponse> search(
      @RequestParam(defaultValue = "false") boolean open,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) String number,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 100, message = "O tamanho da página não pode exceder 100.")
          int size) {
    return service.search(open, patientId, number, page, size);
  }

  @GetMapping("/episode/{episodeId}")
  @PreAuthorize(Authorities.PRESCRIPTION_READERS)
  @Operation(summary = "Prescrições de um episódio")
  public List<PrescriptionResponse> byEpisode(@PathVariable UUID episodeId) {
    return service.byEpisode(episodeId);
  }

  @GetMapping("/{id}")
  @PreAuthorize(Authorities.PRESCRIPTION_READERS)
  @Operation(summary = "Abrir prescrição (fica na auditoria)")
  public PrescriptionResponse view(@PathVariable UUID id) {
    return service.view(id);
  }

  @PostMapping
  @PreAuthorize(Authorities.PRESCRIBERS)
  @Operation(summary = "Emitir prescrição num episódio em curso")
  public ResponseEntity<PrescriptionResponse> create(
      @Valid @RequestBody CreatePrescriptionRequest request) {
    var created = service.create(request);
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PostMapping("/{id}/dispense")
  @PreAuthorize(Authorities.PHARMACISTS)
  @Operation(summary = "Dispensar linhas (total ou parcial; FEFO; tudo ou nada)")
  public PrescriptionResponse dispense(
      @PathVariable UUID id, @Valid @RequestBody DispenseRequest request) {
    return service.dispense(id, request.lines());
  }

  @PatchMapping("/{id}/cancel")
  @PreAuthorize(Authorities.PRESCRIBERS)
  @Operation(summary = "Cancelar o que falta dispensar (motivo obrigatório)")
  public PrescriptionResponse cancel(
      @PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
    return service.cancel(id, request.reason());
  }
}
