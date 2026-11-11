package com.ngolahealth.backend.modules.patients.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.patients.api.PatientDtos.DuplicateCheckRequest;
import com.ngolahealth.backend.modules.patients.api.PatientDtos.PatientRequest;
import com.ngolahealth.backend.modules.patients.api.PatientDtos.PatientResponse;
import com.ngolahealth.backend.modules.patients.api.PatientDtos.PatientSummary;
import com.ngolahealth.backend.modules.patients.application.PatientService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
@Tag(name = "Pacientes", description = "Cadastro de pacientes do hospital")
public class PatientController {

  private final PatientService service;

  @GetMapping
  @PreAuthorize(Authorities.PATIENT_READERS)
  @Operation(summary = "Pesquisar pacientes (nome, processo, BI, cartão ou telefone)")
  public PageResponse<PatientSummary> search(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 100, message = "O tamanho da página não pode exceder 100.")
          int size) {
    return service.search(search, active, page, size);
  }

  @PostMapping("/possible-duplicates")
  @PreAuthorize(Authorities.PATIENT_WRITERS)
  @Operation(summary = "Procurar possíveis duplicados antes de registar")
  public List<PatientSummary> possibleDuplicates(
      @Valid @RequestBody DuplicateCheckRequest request) {
    return service.findPossibleDuplicates(request);
  }

  @GetMapping("/{id}")
  @PreAuthorize(Authorities.PATIENT_READERS)
  @Operation(summary = "Abrir ficha do paciente (a consulta fica na auditoria)")
  public PatientResponse view(@PathVariable UUID id) {
    return service.view(id);
  }

  @PostMapping
  @PreAuthorize(Authorities.PATIENT_WRITERS)
  @Operation(summary = "Registar paciente (o número de processo é atribuído automaticamente)")
  public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientRequest request) {
    var created = service.create(request);
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PutMapping("/{id}")
  @PreAuthorize(Authorities.PATIENT_WRITERS)
  @Operation(summary = "Editar ficha do paciente")
  public PatientResponse update(@PathVariable UUID id, @Valid @RequestBody PatientRequest request) {
    return service.update(id, request);
  }

  @PatchMapping("/{id}/activate")
  @PreAuthorize(Authorities.PATIENT_ADMINS)
  @Operation(summary = "Reactivar paciente")
  public PatientResponse activate(@PathVariable UUID id) {
    return service.setActive(id, true);
  }

  @PatchMapping("/{id}/deactivate")
  @PreAuthorize(Authorities.PATIENT_ADMINS)
  @Operation(summary = "Inactivar paciente (o registo clínico é preservado)")
  public PatientResponse deactivate(@PathVariable UUID id) {
    return service.setActive(id, false);
  }
}
