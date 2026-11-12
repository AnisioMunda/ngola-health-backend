package com.ngolahealth.backend.modules.pharmacy.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.AdjustStockRequest;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.BatchResponse;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.DispenseRequest;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.DispenseResponse;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.MedicationRequest;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.MedicationResponse;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.MovementResponse;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.ReceiveStockRequest;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.StockAlerts;
import com.ngolahealth.backend.modules.pharmacy.application.MedicationService;
import com.ngolahealth.backend.modules.pharmacy.application.StockService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/pharmacy")
@RequiredArgsConstructor
@Tag(name = "Farmácia", description = "Catálogo de medicamentos, lotes, movimentos e dispensa")
public class PharmacyController {

  private final MedicationService catalog;
  private final StockService stock;

  @GetMapping("/medications")
  @PreAuthorize(Authorities.PHARMACY_READERS)
  @Operation(summary = "Pesquisar catálogo (com stock utilizável)")
  public PageResponse<MedicationResponse> search(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 100, message = "O tamanho da página não pode exceder 100.")
          int size) {
    return catalog.search(search, active, page, size);
  }

  @GetMapping("/medications/{id}")
  @PreAuthorize(Authorities.PHARMACY_READERS)
  @Operation(summary = "Obter medicamento")
  public MedicationResponse findById(@PathVariable UUID id) {
    return catalog.findById(id);
  }

  @PostMapping("/medications")
  @PreAuthorize(Authorities.CATALOG_MANAGERS)
  @Operation(summary = "Adicionar medicamento ao catálogo")
  public ResponseEntity<MedicationResponse> create(@Valid @RequestBody MedicationRequest request) {
    var created = catalog.create(request);
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PutMapping("/medications/{id}")
  @PreAuthorize(Authorities.CATALOG_MANAGERS)
  @Operation(summary = "Editar medicamento")
  public MedicationResponse update(
      @PathVariable UUID id, @Valid @RequestBody MedicationRequest request) {
    return catalog.update(id, request);
  }

  @PatchMapping("/medications/{id}/activate")
  @PreAuthorize(Authorities.CATALOG_MANAGERS)
  @Operation(summary = "Reactivar medicamento")
  public MedicationResponse activate(@PathVariable UUID id) {
    return catalog.setActive(id, true);
  }

  @PatchMapping("/medications/{id}/deactivate")
  @PreAuthorize(Authorities.CATALOG_MANAGERS)
  @Operation(summary = "Desactivar medicamento (deixa de poder ser prescrito e dispensado)")
  public MedicationResponse deactivate(@PathVariable UUID id) {
    return catalog.setActive(id, false);
  }

  @GetMapping("/medications/{id}/batches")
  @PreAuthorize(Authorities.PHARMACY_READERS)
  @Operation(summary = "Lotes do medicamento (incluindo esgotados e expirados)")
  public List<BatchResponse> batches(@PathVariable UUID id) {
    return stock.batches(id);
  }

  @GetMapping("/medications/{id}/movements")
  @PreAuthorize(Authorities.PHARMACY_READERS)
  @Operation(summary = "Livro de movimentos do medicamento (mais recentes primeiro)")
  public PageResponse<MovementResponse> movements(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "50")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 200, message = "O tamanho da página não pode exceder 200.")
          int size) {
    return stock.movements(id, page, size);
  }

  @PostMapping("/stock/receive")
  @PreAuthorize(Authorities.PHARMACISTS)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Receber lote")
  public BatchResponse receive(@Valid @RequestBody ReceiveStockRequest request) {
    return stock.receive(request);
  }

  @PostMapping("/batches/{id}/adjust")
  @PreAuthorize(Authorities.PHARMACISTS)
  @Operation(summary = "Acerto de inventário ou abate por dano (motivo obrigatório)")
  public BatchResponse adjust(
      @PathVariable UUID id, @Valid @RequestBody AdjustStockRequest request) {
    return stock.adjust(id, request);
  }

  @PostMapping("/batches/{id}/write-off-expired")
  @PreAuthorize(Authorities.PHARMACISTS)
  @Operation(summary = "Abater o saldo de um lote expirado")
  public BatchResponse writeOffExpired(@PathVariable UUID id) {
    return stock.writeOffExpired(id);
  }

  @PostMapping("/dispense")
  @PreAuthorize(Authorities.PHARMACISTS)
  @Operation(summary = "Dispensa directa (só venda livre; FEFO)")
  public DispenseResponse dispense(@Valid @RequestBody DispenseRequest request) {
    return stock.dispenseOverTheCounter(request);
  }

  @GetMapping("/alerts")
  @PreAuthorize(Authorities.PHARMACY_READERS)
  @Operation(summary = "Stock abaixo do mínimo, lotes a expirar e expirados com saldo")
  public StockAlerts alerts(@RequestParam(defaultValue = "90") int days) {
    return stock.alerts(days);
  }
}
