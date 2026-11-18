package com.ngolahealth.backend.modules.portal.api;

import com.ngolahealth.backend.modules.portal.api.PortalDtos.ActivationRequest;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.CancelRequest;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalAppointment;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalInvoice;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalLabRequest;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalPrescription;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalProfile;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalVisit;
import com.ngolahealth.backend.modules.portal.application.PortalActivationService;
import com.ngolahealth.backend.modules.portal.application.PortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Portal do paciente: só contas de doente (token com a audiência do portal). */
@RestController
@RequestMapping("/portal")
@RequiredArgsConstructor
@Tag(name = "Portal do paciente", description = "Activação da conta e dados do próprio doente")
public class PortalController {

  private static final String PATIENT = "hasRole('PATIENT')";

  private final PortalActivationService activation;
  private final PortalService portal;

  @PostMapping("/activate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Activar a conta com o código entregue na recepção (público)")
  public void activate(@Valid @RequestBody ActivationRequest request) {
    activation.activate(request);
  }

  @GetMapping("/me")
  @PreAuthorize(PATIENT)
  public PortalProfile me() {
    return portal.profile();
  }

  @GetMapping("/appointments")
  @PreAuthorize(PATIENT)
  public List<PortalAppointment> appointments() {
    return portal.appointments();
  }

  @PatchMapping("/appointments/{id}/cancel")
  @PreAuthorize(PATIENT)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelAppointment(@PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
    portal.cancelAppointment(id, request.reason());
  }

  @GetMapping("/lab-results")
  @PreAuthorize(PATIENT)
  @Operation(summary = "Resultados validados")
  public List<PortalLabRequest> labResults() {
    return portal.labResults();
  }

  @GetMapping("/prescriptions")
  @PreAuthorize(PATIENT)
  public List<PortalPrescription> prescriptions() {
    return portal.prescriptions();
  }

  @GetMapping("/invoices")
  @PreAuthorize(PATIENT)
  public List<PortalInvoice> invoices() {
    return portal.invoices();
  }

  @GetMapping("/visits")
  @PreAuthorize(PATIENT)
  public List<PortalVisit> visits() {
    return portal.visits();
  }
}
