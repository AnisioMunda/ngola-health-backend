package com.ngolahealth.backend.modules.prescriptions.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.episodes.application.EpisodeDirectory;
import com.ngolahealth.backend.modules.episodes.domain.EpisodeStatus;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory.PatientRef;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.MedicationResponse;
import com.ngolahealth.backend.modules.pharmacy.application.MedicationService;
import com.ngolahealth.backend.modules.pharmacy.application.StockService;
import com.ngolahealth.backend.modules.pharmacy.domain.MovementContext;
import com.ngolahealth.backend.modules.prescriptions.api.PrescriptionDtos.CreatePrescriptionRequest;
import com.ngolahealth.backend.modules.prescriptions.api.PrescriptionDtos.DispenseLineRequest;
import com.ngolahealth.backend.modules.prescriptions.api.PrescriptionDtos.ItemResponse;
import com.ngolahealth.backend.modules.prescriptions.api.PrescriptionDtos.PrescriptionResponse;
import com.ngolahealth.backend.modules.prescriptions.domain.ItemDetails;
import com.ngolahealth.backend.modules.prescriptions.domain.Prescription;
import com.ngolahealth.backend.modules.prescriptions.domain.PrescriptionNumbers;
import com.ngolahealth.backend.modules.prescriptions.domain.PrescriptionRepository;
import com.ngolahealth.backend.modules.prescriptions.domain.PrescriptionStatus;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Emissão, consulta, dispensa e cancelamento de prescrições. */
@Service
@RequiredArgsConstructor
public class PrescriptionService {

  static final String NOT_FOUND = "PRESCRIPTION_NOT_FOUND";
  static final int DEFAULT_VALIDITY_DAYS = 30;

  private final PrescriptionRepository prescriptions;
  private final PrescriptionNumbers numbers;
  private final EpisodeDirectory episodes;
  private final PatientDirectory patients;
  private final MedicationService medications;
  private final StockService stock;
  private final StaffDirectory staff;
  private final CurrentUserProvider currentUser;
  private final AuditTrail audit;
  private final AppProperties app;
  private final Clock clock;

  /** "open" = activas e parcialmente dispensadas (lista de trabalho da farmácia). */
  @Transactional(readOnly = true)
  public PageResponse<PrescriptionResponse> search(
      boolean onlyOpen, UUID patientId, String number, int page, int size) {
    requireHospitalUser();
    var statuses =
        onlyOpen
            ? EnumSet.of(PrescriptionStatus.ACTIVE, PrescriptionStatus.PARTIALLY_DISPENSED)
            : EnumSet.allOf(PrescriptionStatus.class);
    var result =
        prescriptions.search(
            statuses,
            patientId,
            number == null || number.isBlank() ? null : number.strip().toUpperCase(),
            PageRequest.of(page, size, Sort.by(Sort.Order.desc("issuedAt"))));
    var context = context(result.getContent());
    return PageResponse.of(result, p -> toResponse(p, context));
  }

  @Transactional(readOnly = true)
  public List<PrescriptionResponse> byEpisode(UUID episodeId) {
    requireHospitalUser();
    var list = prescriptions.findByEpisode(episodeId);
    var context = context(list);
    return list.stream().map(p -> toResponse(p, context)).toList();
  }

  @Transactional
  public PrescriptionResponse view(UUID id) {
    requireHospitalUser();
    var p = load(id);
    var response = toResponse(p, context(List.of(p)));
    audit.record(entry(AuditAction.VIEW, p, "Prescrição consultada: " + describe(response)));
    return response;
  }

  @Transactional
  public PrescriptionResponse create(CreatePrescriptionRequest r) {
    var user = requireHospitalUser();
    var episode =
        episodes
            .find(r.episodeId())
            .orElseThrow(
                () -> new BadRequestException("EPISODE_NOT_FOUND", "O episódio não existe."));
    if (episode.status() != EpisodeStatus.IN_PROGRESS) {
      throw new ConflictException(
          "EPISODE_NOT_IN_PROGRESS", "Só se prescreve num episódio em curso.");
    }
    var meds = medications.findByIds(r.items().stream().map(i -> i.medicationId()).toList());
    for (var item : r.items()) {
      var med = meds.get(item.medicationId());
      if (med == null) {
        throw new BadRequestException(
            "MEDICATION_NOT_FOUND", "Um dos medicamentos não existe no catálogo.");
      }
      if (!med.active()) {
        throw new ConflictException(
            "MEDICATION_INACTIVE", med.name() + " está desactivado no catálogo.");
      }
    }
    var lines =
        r.items().stream()
            .map(
                i ->
                    new ItemDetails(
                        i.medicationId(),
                        i.quantity(),
                        i.dosage().strip(),
                        i.frequency().strip(),
                        i.durationDays(),
                        i.route(),
                        optional(i.instructions())))
            .toList();
    var today = today();
    var p =
        prescriptions.saveAndFlush(
            new Prescription(
                numbers.next(user.hospitalId(), today.getYear()),
                episode.patientId(),
                episode.id(),
                user.id(),
                clock.instant(),
                today.plusDays(r.validityDays() == null ? DEFAULT_VALIDITY_DAYS : r.validityDays()),
                optional(r.notes()),
                lines));
    var response = toResponse(p, context(List.of(p)));
    audit.record(
        entry(AuditAction.CREATE, p, "Prescrição emitida: " + describe(response))
            .withChanges(audit.snapshot(response)));
    return response;
  }

  /**
   * Dispensa uma ou mais linhas, tudo ou nada: com a prescrição bloqueada, retira o stock por FEFO
   * (cada saída fica no livro de stock ligada à linha) e actualiza as quantidades.
   */
  @Transactional
  public PrescriptionResponse dispense(UUID id, List<DispenseLineRequest> lines) {
    requireHospitalUser();
    var p =
        prescriptions
            .findForUpdate(id)
            .orElseThrow(() -> new NotFoundException(NOT_FOUND, "Prescrição não encontrada."));
    var today = today();
    p.ensureDispensable(today);
    Set<UUID> seen = new HashSet<>();
    for (var line : lines) {
      if (!seen.add(line.itemId())) {
        throw new BadRequestException(
            "DUPLICATE_LINE", "A mesma linha aparece duas vezes no pedido.");
      }
    }
    var parts = new StringBuilder();
    for (var line : lines) {
      var item = p.item(line.itemId());
      p.recordDispensed(item.getId(), line.quantity(), today);
      var result =
          stock.dispenseForPrescription(
              item.getMedicationId(),
              line.quantity(),
              new MovementContext(
                  p.getPatientId(), p.getEpisodeId(), item.getId(), "Prescrição " + p.getNumber()));
      parts
          .append(parts.isEmpty() ? "" : "; ")
          .append(result.medicationName())
          .append(" ×")
          .append(line.quantity());
    }
    var saved = prescriptions.saveAndFlush(p);
    var response = toResponse(saved, context(List.of(saved)));
    audit.record(
        entry(
            AuditAction.DISPENSE,
            saved,
            "Dispensa da prescrição " + saved.getNumber() + ": " + parts));
    return response;
  }

  @Transactional
  public PrescriptionResponse cancel(UUID id, String reason) {
    var user = requireHospitalUser();
    var p = load(id);
    p.cancel(reason.strip(), user.id(), clock.instant());
    var saved = prescriptions.saveAndFlush(p);
    var response = toResponse(saved, context(List.of(saved)));
    audit.record(
        entry(
            AuditAction.CANCEL,
            saved,
            "Prescrição cancelada (" + reason.strip() + "): " + describe(response)));
    return response;
  }

  // ---------- Resposta ----------

  private record Context(
      Map<UUID, PatientRef> patients,
      Map<UUID, MedicationResponse> meds,
      Map<UUID, String> names) {}

  private Context context(List<Prescription> list) {
    var patientRefs =
        patients.findAll(list.stream().map(Prescription::getPatientId).distinct().toList());
    var meds =
        medications.findByIds(
            list.stream()
                .flatMap(p -> p.getItems().stream())
                .map(i -> i.getMedicationId())
                .collect(Collectors.toSet()));
    Set<UUID> people = new HashSet<>();
    list.forEach(
        p -> {
          people.add(p.getPrescriberId());
          if (p.getCancelledBy() != null) {
            people.add(p.getCancelledBy());
          }
        });
    return new Context(patientRefs, meds, new HashMap<>(staff.names(people)));
  }

  private PrescriptionResponse toResponse(Prescription p, Context c) {
    var today = today();
    var patient = c.patients().get(p.getPatientId());
    var items =
        p.getItems().stream()
            .map(
                i -> {
                  var med = c.meds().get(i.getMedicationId());
                  return new ItemResponse(
                      i.getId(),
                      i.getLineNumber(),
                      i.getMedicationId(),
                      med == null
                          ? null
                          : med.name() + (med.strength() == null ? "" : " " + med.strength()),
                      med == null ? null : med.dispensingUnit(),
                      i.getQuantity(),
                      i.getQuantityDispensed(),
                      i.remaining(),
                      i.getDosage(),
                      i.getFrequency(),
                      i.getDurationDays(),
                      i.getRoute(),
                      i.getInstructions(),
                      med == null ? 0 : med.stockAvailable());
                })
            .toList();
    return new PrescriptionResponse(
        p.getId(),
        p.getNumber(),
        p.getPatientId(),
        patient == null ? null : patient.fullName(),
        patient == null ? null : patient.recordNumber(),
        p.getEpisodeId(),
        p.getPrescriberId(),
        c.names().get(p.getPrescriberId()),
        p.getStatus(),
        p.isExpired(today) ? "EXPIRED" : p.getStatus().name(),
        p.getIssuedAt(),
        p.getValidUntil(),
        p.getNotes(),
        p.getCancelledAt(),
        p.getCancelledBy() == null ? null : c.names().get(p.getCancelledBy()),
        p.getCancellationReason(),
        items,
        p.getVersion());
  }

  private AuditEntry entry(AuditAction action, Prescription p, String summary) {
    return AuditEntry.success(
        action, AuditEntityType.PRESCRIPTION, p.getId(), p.getHospitalId(), summary);
  }

  private static String describe(PrescriptionResponse r) {
    return r.number() + " — " + r.patientName() + " (processo " + r.patientRecordNumber() + ")";
  }

  private Prescription load(UUID id) {
    return prescriptions
        .findScoped(id)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND, "Prescrição não encontrada."));
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), app.timeZone());
  }

  private CurrentUser requireHospitalUser() {
    var user = currentUser.get();
    if (user.hospitalId() == null) {
      throw new ForbiddenException(
          "HOSPITAL_SCOPE_REQUIRED",
          "Os dados clínicos só estão acessíveis a utilizadores de um hospital.");
    }
    return user;
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
