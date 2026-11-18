package com.ngolahealth.backend.modules.portal.api;

import com.ngolahealth.backend.modules.portal.api.PortalDtos.IssuedCode;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalStatus;
import com.ngolahealth.backend.modules.portal.application.PortalAccessService;
import com.ngolahealth.backend.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestão, pelo pessoal, do acesso de um doente ao portal. */
@RestController
@RequestMapping("/patients/{patientId}/portal")
@RequiredArgsConstructor
@Tag(name = "Portal do paciente (gestão)")
public class PatientPortalAccessController {

  private final PortalAccessService access;

  @GetMapping
  @PreAuthorize(Authorities.PATIENT_READERS)
  public PortalStatus status(@PathVariable UUID patientId) {
    return access.status(patientId);
  }

  @PostMapping("/activation-code")
  @PreAuthorize(Authorities.PATIENT_WRITERS)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Gerar código de activação (entregar em mão, com a identidade verificada)")
  public IssuedCode issueCode(@PathVariable UUID patientId) {
    return access.issueCode(patientId);
  }

  @PatchMapping("/deactivate")
  @PreAuthorize(Authorities.PATIENT_ADMINS)
  public PortalStatus deactivate(@PathVariable UUID patientId) {
    return access.setActive(patientId, false);
  }

  @PatchMapping("/activate")
  @PreAuthorize(Authorities.PATIENT_ADMINS)
  public PortalStatus reactivate(@PathVariable UUID patientId) {
    return access.setActive(patientId, true);
  }
}
