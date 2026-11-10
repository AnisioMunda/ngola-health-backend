package com.ngolahealth.backend.modules.hospitals.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.hospitals.application.HospitalService;
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
@RequestMapping("/hospitals")
@RequiredArgsConstructor
@Tag(name = "Hospitais", description = "Gestão das instituições de saúde da plataforma")
public class HospitalController {

  private final HospitalService service;

  @GetMapping
  @PreAuthorize(Authorities.AUTHENTICATED)
  @Operation(summary = "Listar hospitais activos (para selecção)")
  public List<HospitalResponse> findActive() {
    return service.findActive();
  }

  @GetMapping("/management")
  @PreAuthorize(Authorities.SUPER_ADMIN)
  @Operation(summary = "Pesquisar todos os hospitais, incluindo inactivos (paginado)")
  public PageResponse<HospitalResponse> search(
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

  @GetMapping("/{id}")
  @PreAuthorize(Authorities.AUTHENTICATED)
  @Operation(summary = "Obter um hospital")
  public HospitalResponse findById(@PathVariable UUID id) {
    return service.findById(id);
  }

  @PostMapping
  @PreAuthorize(Authorities.SUPER_ADMIN)
  @Operation(summary = "Registar hospital")
  public ResponseEntity<HospitalResponse> create(@Valid @RequestBody HospitalRequest request) {
    var created = service.create(request);
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PutMapping("/{id}")
  @PreAuthorize(Authorities.SUPER_ADMIN)
  @Operation(summary = "Editar hospital")
  public HospitalResponse update(
      @PathVariable UUID id, @Valid @RequestBody HospitalRequest request) {
    return service.update(id, request);
  }

  @PatchMapping("/{id}/activate")
  @PreAuthorize(Authorities.SUPER_ADMIN)
  @Operation(summary = "Activar hospital")
  public HospitalResponse activate(@PathVariable UUID id) {
    return service.setActive(id, true);
  }

  @PatchMapping("/{id}/deactivate")
  @PreAuthorize(Authorities.SUPER_ADMIN)
  @Operation(summary = "Desactivar hospital (os registos associados são preservados)")
  public HospitalResponse deactivate(@PathVariable UUID id) {
    return service.setActive(id, false);
  }
}
