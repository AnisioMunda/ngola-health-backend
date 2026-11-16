package com.ngolahealth.backend.modules.inpatient.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.AdmissionResponse;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.AdmitRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.AssignDoctorRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.BedRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.BedResponse;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.BedStatusRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.CancelRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.DischargeRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.ExpectedDischargeRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.TransferRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.WardMap;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.WardRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.WardResponse;
import com.ngolahealth.backend.modules.inpatient.application.AdmissionService;
import com.ngolahealth.backend.modules.inpatient.application.WardService;
import com.ngolahealth.backend.modules.inpatient.domain.AdmissionStatus;
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
@RequestMapping("/inpatient")
@RequiredArgsConstructor
@Tag(
    name = "Internamento",
    description = "Enfermarias, camas, internamentos, transferências e altas")
public class InpatientController {

  private final WardService wards;
  private final AdmissionService admissions;

  // ---------- Enfermarias ----------

  @GetMapping("/wards")
  @PreAuthorize(Authorities.INPATIENT_READERS)
  @Operation(summary = "Enfermarias com contagem de camas por estado")
  public List<WardResponse> wards(@RequestParam(defaultValue = "false") boolean includeInactive) {
    return wards.wards(includeInactive);
  }

  @GetMapping("/wards/{id}/map")
  @PreAuthorize(Authorities.INPATIENT_READERS)
  @Operation(summary = "Mapa de camas da enfermaria, com o doente de cada cama ocupada")
  public WardMap map(
      @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean includeInactive) {
    return wards.map(id, includeInactive);
  }

  @PostMapping("/wards")
  @PreAuthorize(Authorities.WARD_MANAGERS)
  @ResponseStatus(HttpStatus.CREATED)
  public WardResponse createWard(@Valid @RequestBody WardRequest request) {
    return wards.createWard(request);
  }

  @PutMapping("/wards/{id}")
  @PreAuthorize(Authorities.WARD_MANAGERS)
  public WardResponse updateWard(@PathVariable UUID id, @Valid @RequestBody WardRequest request) {
    return wards.updateWard(id, request);
  }

  @PatchMapping("/wards/{id}/activate")
  @PreAuthorize(Authorities.WARD_MANAGERS)
  public WardResponse activateWard(@PathVariable UUID id) {
    return wards.setWardActive(id, true);
  }

  @PatchMapping("/wards/{id}/deactivate")
  @PreAuthorize(Authorities.WARD_MANAGERS)
  public WardResponse deactivateWard(@PathVariable UUID id) {
    return wards.setWardActive(id, false);
  }

  // ---------- Camas ----------

  @PostMapping("/beds")
  @PreAuthorize(Authorities.WARD_MANAGERS)
  @ResponseStatus(HttpStatus.CREATED)
  public BedResponse createBed(@Valid @RequestBody BedRequest request) {
    return wards.createBed(request);
  }

  @PutMapping("/beds/{id}")
  @PreAuthorize(Authorities.WARD_MANAGERS)
  public BedResponse updateBed(@PathVariable UUID id, @Valid @RequestBody BedRequest request) {
    return wards.updateBed(id, request);
  }

  @PatchMapping("/beds/{id}/status")
  @PreAuthorize(Authorities.BED_KEEPERS)
  @Operation(summary = "Limpeza/manutenção (a ocupação só muda pelo internamento)")
  public BedResponse bedStatus(
      @PathVariable UUID id, @Valid @RequestBody BedStatusRequest request) {
    return wards.setBedStatus(id, request);
  }

  @PatchMapping("/beds/{id}/activate")
  @PreAuthorize(Authorities.WARD_MANAGERS)
  public BedResponse activateBed(@PathVariable UUID id) {
    return wards.setBedActive(id, true);
  }

  @PatchMapping("/beds/{id}/deactivate")
  @PreAuthorize(Authorities.WARD_MANAGERS)
  public BedResponse deactivateBed(@PathVariable UUID id) {
    return wards.setBedActive(id, false);
  }

  // ---------- Internamentos ----------

  @GetMapping("/admissions")
  @PreAuthorize(Authorities.INPATIENT_READERS)
  public PageResponse<AdmissionResponse> admissions(
      @RequestParam(required = false) AdmissionStatus status,
      @RequestParam(required = false) UUID wardId,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 100, message = "O tamanho da página não pode exceder 100.")
          int size) {
    return admissions.search(status, wardId, patientId, doctorId, page, size);
  }

  @GetMapping("/admissions/{id}")
  @PreAuthorize(Authorities.INPATIENT_READERS)
  public AdmissionResponse admission(@PathVariable UUID id) {
    return admissions.view(id);
  }

  @GetMapping("/admissions/episode/{episodeId}")
  @PreAuthorize(Authorities.INPATIENT_READERS)
  @Operation(summary = "Internamento a que pertence um episódio de internamento")
  public AdmissionResponse byEpisode(@PathVariable UUID episodeId) {
    return admissions.byEpisode(episodeId);
  }

  @PostMapping("/admissions")
  @PreAuthorize(Authorities.ADMITTERS)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Internar (abre o episódio de internamento e ocupa a cama)")
  public AdmissionResponse admit(@Valid @RequestBody AdmitRequest request) {
    return admissions.admit(request);
  }

  @PostMapping("/admissions/{id}/transfer")
  @PreAuthorize(Authorities.BED_TRANSFERRERS)
  public AdmissionResponse transfer(
      @PathVariable UUID id, @Valid @RequestBody TransferRequest request) {
    return admissions.transfer(id, request);
  }

  @PostMapping("/admissions/{id}/discharge")
  @PreAuthorize(Authorities.ADMITTERS)
  @Operation(summary = "Alta (conclui o episódio; a cama fica para limpeza)")
  public AdmissionResponse discharge(
      @PathVariable UUID id, @Valid @RequestBody DischargeRequest request) {
    return admissions.discharge(id, request);
  }

  @PatchMapping("/admissions/{id}/cancel")
  @PreAuthorize(Authorities.ADMITTERS)
  @Operation(summary = "Anular um internamento registado por engano (até 24 h)")
  public AdmissionResponse cancel(
      @PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
    return admissions.cancel(id, request.reason());
  }

  @PatchMapping("/admissions/{id}/doctor")
  @PreAuthorize(Authorities.ADMITTERS)
  public AdmissionResponse assignDoctor(
      @PathVariable UUID id, @Valid @RequestBody AssignDoctorRequest request) {
    return admissions.assignDoctor(id, request.doctorId());
  }

  @PatchMapping("/admissions/{id}/expected-discharge")
  @PreAuthorize(Authorities.BED_TRANSFERRERS)
  public AdmissionResponse expectedDischarge(
      @PathVariable UUID id, @RequestBody ExpectedDischargeRequest request) {
    return admissions.setExpectedDischarge(id, request.expectedDischargeDate());
  }
}
