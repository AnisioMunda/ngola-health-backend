package com.ngolahealth.backend.modules.portal.application;

import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.modules.episodes.application.EpisodeService;
import com.ngolahealth.backend.modules.episodes.application.EpisodeService.EpisodeSearch;
import com.ngolahealth.backend.modules.hospitals.application.HospitalService;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.ItemResponse;
import com.ngolahealth.backend.modules.laboratory.application.LabRequestService;
import com.ngolahealth.backend.modules.laboratory.domain.ItemStatus;
import com.ngolahealth.backend.modules.patients.application.PatientService;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalAppointment;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalLabRequest;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalPrescription;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalPrescriptionItem;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalProfile;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalResult;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalVisit;
import com.ngolahealth.backend.modules.prescriptions.application.PrescriptionService;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.AppointmentResponse;
import com.ngolahealth.backend.modules.scheduling.application.AppointmentService;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Dados do próprio doente. O doente vem sempre da sessão (nunca de um parâmetro do pedido) e as
 * respostas são reduzidas ao que lhe diz respeito: sem notas internas nem resultados por validar.
 */
@Service
@RequiredArgsConstructor
public class PortalService {

  private final CurrentUserProvider currentUser;
  private final PatientService patients;
  private final HospitalService hospitals;
  private final AppointmentService appointments;
  private final LabRequestService lab;
  private final PrescriptionService prescriptions;
  private final EpisodeService episodes;
  private final AppProperties app;
  private final Clock clock;

  UUID patientId() {
    var user = currentUser.get();
    if (!user.isPortal()) {
      throw new ForbiddenException("PORTAL_ONLY", "Área do portal do paciente.");
    }
    return user.patientId();
  }

  public PortalProfile profile() {
    var p = patients.view(patientId());
    var hospital =
        hospitals.findSummary(currentUser.get().hospitalId()).map(h -> h.name()).orElse(null);
    return new PortalProfile(
        p.fullName(), p.recordNumber(), p.birthDate(), hospital, p.phone(), p.email());
  }

  /**
   * Consultas do último ano e as futuras. Dois pedidos, porque a pesquisa do agendamento aceita no
   * máximo 366 dias de cada vez (as marcações futuras já estão limitadas a esse horizonte).
   */
  public List<PortalAppointment> appointments() {
    var today = today();
    var patient = patientId();
    var past =
        appointments.search(today.minusDays(365), today.minusDays(1), null, patient, null, 0, 200);
    var upcoming = appointments.search(today, today.plusDays(365), null, patient, null, 0, 200);
    return java.util.stream.Stream.concat(past.content().stream(), upcoming.content().stream())
        .sorted(
            Comparator.comparing(AppointmentResponse::date)
                .thenComparing(AppointmentResponse::startTime)
                .reversed())
        .map(
            a ->
                new PortalAppointment(
                    a.id(),
                    a.date(),
                    a.startTime(),
                    a.endTime(),
                    a.doctorName(),
                    a.type().name(),
                    a.status().name(),
                    a.reason(),
                    a.status().isPending() && !a.date().isBefore(today)))
        .toList();
  }

  public void cancelAppointment(UUID id, String reason) {
    var a = appointments.view(id);
    if (!a.patientId().equals(patientId())) {
      throw new NotFoundException("APPOINTMENT_NOT_FOUND", "Marcação não encontrada.");
    }
    appointments.cancel(id, "Cancelada pelo doente no portal: " + reason.strip());
  }

  /** Só resultados validados; os outros contam como "por concluir". */
  public List<PortalLabRequest> labResults() {
    return lab.search(false, patientId(), null, 0, 100).content().stream()
        .filter(r -> !"CANCELLED".equals(r.status().name()))
        .map(
            r -> {
              var validated =
                  r.items().stream().filter(i -> i.status() == ItemStatus.VALIDATED).toList();
              int pending =
                  (int)
                      r.items().stream()
                          .filter(
                              i ->
                                  i.status() != ItemStatus.VALIDATED
                                      && i.status() != ItemStatus.CANCELLED)
                          .count();
              return new PortalLabRequest(
                  r.id(),
                  r.number(),
                  r.requestedAt(),
                  r.requestedByName(),
                  r.status().name(),
                  validated.stream().map(PortalService::result).toList(),
                  pending);
            })
        .toList();
  }

  public List<PortalPrescription> prescriptions() {
    return prescriptions.search(false, patientId(), null, 0, 100).content().stream()
        .filter(p -> !"CANCELLED".equals(p.status().name()))
        .map(
            p ->
                new PortalPrescription(
                    p.id(),
                    p.number(),
                    p.issuedAt(),
                    p.prescriberName(),
                    p.effectiveStatus(),
                    p.validUntil(),
                    p.items().stream()
                        .map(
                            i ->
                                new PortalPrescriptionItem(
                                    i.medicationName(),
                                    String.join(
                                        " · ",
                                        java.util.stream.Stream.of(
                                                i.dosage(),
                                                i.frequency(),
                                                i.durationDays() == null
                                                    ? null
                                                    : i.durationDays() + " dias",
                                                i.instructions())
                                            .filter(s -> s != null && !s.isBlank())
                                            .toList()),
                                    i.remaining() == 0 ? "DISPENSED" : "PENDING"))
                        .toList()))
        .toList();
  }

  public List<PortalVisit> visits() {
    return episodes
        .search(new EpisodeSearch(null, patientId(), null, null, null, null, true), 0, 100)
        .content()
        .stream()
        .map(
            e ->
                new PortalVisit(
                    e.id(),
                    e.scheduledAt(),
                    e.type().name(),
                    e.status().name(),
                    e.doctorName(),
                    e.reason()))
        .toList();
  }

  private static PortalResult result(ItemResponse i) {
    String value =
        i.numericValue() != null
            ? i.numericValue().stripTrailingZeros().toPlainString()
            : i.textValue();
    String reference =
        i.referenceText() != null
            ? i.referenceText()
            : i.referenceLow() != null || i.referenceHigh() != null
                ? (i.referenceLow() == null
                        ? ""
                        : i.referenceLow().stripTrailingZeros().toPlainString())
                    + "–"
                    + (i.referenceHigh() == null
                        ? ""
                        : i.referenceHigh().stripTrailingZeros().toPlainString())
                : null;
    return new PortalResult(
        i.testName(),
        value,
        i.unit(),
        reference,
        i.flag() == null ? null : i.flag().name(),
        i.validatedAt());
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), app.timeZone());
  }
}
