package com.ngolahealth.backend.modules.equipment.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentOverview;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentRequest;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentResponse;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentSummary;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.FaultRequest;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.FaultResponse;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.MaintenanceRequest;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.ReasonRequest;
import com.ngolahealth.backend.modules.equipment.application.EquipmentService;
import com.ngolahealth.backend.modules.equipment.application.FaultService;
import com.ngolahealth.backend.modules.equipment.application.MaintenanceService;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentCategory;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentStatus;
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
@RequestMapping("/equipment")
@RequiredArgsConstructor
@Tag(name = "Equipamentos", description = "Inventário, avarias e manutenções")
public class EquipmentController {

  private final EquipmentService equipment;
  private final FaultService faults;
  private final MaintenanceService maintenance;

  @GetMapping
  @PreAuthorize(Authorities.STAFF)
  public PageResponse<EquipmentSummary> search(
      @RequestParam(required = false) EquipmentStatus status,
      @RequestParam(required = false) EquipmentCategory category,
      @RequestParam(required = false) UUID wardId,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "false") boolean includeRetired,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 100, message = "O tamanho da página não pode exceder 100.")
          int size) {
    return equipment.search(status, category, wardId, search, includeRetired, page, size);
  }

  @GetMapping("/overview")
  @PreAuthorize(Authorities.STAFF)
  public EquipmentOverview overview() {
    return equipment.overview();
  }

  @GetMapping("/due")
  @PreAuthorize(Authorities.STAFF)
  @Operation(summary = "Manutenções/calibrações vencidas ou a vencer (críticos primeiro)")
  public List<EquipmentSummary> due(@RequestParam(defaultValue = "30") int days) {
    return equipment.due(days);
  }

  @GetMapping("/faults")
  @PreAuthorize(Authorities.STAFF)
  @Operation(summary = "Avarias em aberto")
  public List<FaultResponse> openFaults() {
    return faults.open();
  }

  @GetMapping("/{id}")
  @PreAuthorize(Authorities.STAFF)
  public EquipmentResponse view(@PathVariable UUID id) {
    return equipment.view(id);
  }

  @PostMapping
  @PreAuthorize(Authorities.EQUIPMENT_MANAGERS)
  @ResponseStatus(HttpStatus.CREATED)
  public EquipmentResponse create(@Valid @RequestBody EquipmentRequest request) {
    return equipment.create(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize(Authorities.EQUIPMENT_MANAGERS)
  public EquipmentResponse update(
      @PathVariable UUID id, @Valid @RequestBody EquipmentRequest request) {
    return equipment.update(id, request);
  }

  @PostMapping("/{id}/faults")
  @PreAuthorize(Authorities.STAFF)
  @Operation(summary = "Reportar avaria (qualquer profissional)")
  public EquipmentResponse reportFault(
      @PathVariable UUID id, @Valid @RequestBody FaultRequest request) {
    return faults.report(id, request);
  }

  @PatchMapping("/faults/{faultId}/dismiss")
  @PreAuthorize(Authorities.EQUIPMENT_MANAGERS)
  public EquipmentResponse dismissFault(
      @PathVariable UUID faultId, @Valid @RequestBody ReasonRequest request) {
    return faults.dismiss(faultId, request.reason());
  }

  @PatchMapping("/{id}/send-to-maintenance")
  @PreAuthorize(Authorities.EQUIPMENT_MANAGERS)
  public EquipmentResponse sendToMaintenance(@PathVariable UUID id) {
    return equipment.sendToMaintenance(id);
  }

  @PostMapping("/{id}/maintenance")
  @PreAuthorize(Authorities.EQUIPMENT_MANAGERS)
  @Operation(summary = "Registar manutenção, calibração ou inspecção")
  public EquipmentResponse recordMaintenance(
      @PathVariable UUID id, @Valid @RequestBody MaintenanceRequest request) {
    return maintenance.record(id, request);
  }

  @PatchMapping("/{id}/retire")
  @PreAuthorize(Authorities.EQUIPMENT_MANAGERS)
  public EquipmentResponse retire(
      @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
    return equipment.retire(id, request.reason());
  }
}
