package com.ngolahealth.backend.modules.laboratory.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.episodes.application.EpisodeDirectory;
import com.ngolahealth.backend.modules.episodes.domain.EpisodeStatus;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.AmendRequest;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.AmendmentView;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.CreateLabRequest;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.ItemResponse;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.LabRequestResponse;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.ResultRequest;
import com.ngolahealth.backend.modules.laboratory.domain.ItemStatus;
import com.ngolahealth.backend.modules.laboratory.domain.LabRequest;
import com.ngolahealth.backend.modules.laboratory.domain.LabRequestItem;
import com.ngolahealth.backend.modules.laboratory.domain.LabRequestNumbers;
import com.ngolahealth.backend.modules.laboratory.domain.LabRequestRepository;
import com.ngolahealth.backend.modules.laboratory.domain.LabRequestStatus;
import com.ngolahealth.backend.modules.laboratory.domain.LabTest;
import com.ngolahealth.backend.modules.laboratory.domain.LabTestRepository;
import com.ngolahealth.backend.modules.laboratory.domain.ResultAmendment;
import com.ngolahealth.backend.modules.laboratory.domain.ResultAmendmentRepository;
import com.ngolahealth.backend.modules.laboratory.domain.ResultFlag;
import com.ngolahealth.backend.modules.laboratory.domain.ResultValue;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory.PatientRef;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.security.CurrentUserProvider;
import com.ngolahealth.backend.security.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedidos de exames: emissão, colheita, análise, resultados, validação, correcção e cancelamento.
 */
@Service
@RequiredArgsConstructor
public class LabRequestService {

  private final LabRequestRepository requests;
  private final LabTestRepository tests;
  private final ResultAmendmentRepository amendments;
  private final LabRequestNumbers numbers;
  private final LabTestService catalog;
  private final EpisodeDirectory episodes;
  private final PatientDirectory patients;
  private final StaffDirectory staff;
  private final CurrentUserProvider currentUser;
  private final AuditTrail audit;
  private final AppProperties app;
  private final Clock clock;

  /** "open" = ainda não concluídos nem cancelados (lista de trabalho do laboratório). */
  @Transactional(readOnly = true)
  public PageResponse<LabRequestResponse> search(
      boolean onlyOpen, UUID patientId, String number, int page, int size) {
    catalog.requireHospitalUser();
    var statuses =
        onlyOpen
            ? EnumSet.of(
                LabRequestStatus.PENDING, LabRequestStatus.COLLECTED, LabRequestStatus.IN_ANALYSIS)
            : EnumSet.allOf(LabRequestStatus.class);
    var result =
        requests.search(
            statuses,
            patientId,
            number == null || number.isBlank() ? null : number.strip().toUpperCase(),
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("priority"), Sort.Order.asc("requestedAt"))));
    var ctx = context(result.getContent(), false);
    return PageResponse.of(result, r -> toResponse(r, ctx));
  }

  @Transactional(readOnly = true)
  public List<LabRequestResponse> byEpisode(UUID episodeId) {
    catalog.requireHospitalUser();
    var list = requests.findByEpisode(episodeId);
    var ctx = context(list, false);
    return list.stream().map(r -> toResponse(r, ctx)).toList();
  }

  @Transactional
  public LabRequestResponse view(UUID id) {
    catalog.requireHospitalUser();
    var r = load(id);
    var response = toResponse(r, context(List.of(r), true));
    audit.record(entry(AuditAction.VIEW, r, "Pedido de exames consultado: " + describe(response)));
    return response;
  }

  @Transactional
  public LabRequestResponse create(CreateLabRequest req) {
    var user = currentUser.get();
    catalog.requireHospitalUser();
    var episode =
        episodes
            .find(req.episodeId())
            .orElseThrow(
                () -> new BadRequestException("EPISODE_NOT_FOUND", "O episódio não existe."));
    if (episode.status() != EpisodeStatus.IN_PROGRESS) {
      throw new ConflictException(
          "EPISODE_NOT_IN_PROGRESS", "Só se pedem exames num episódio em curso.");
    }
    var found =
        tests.findAllScoped(req.testIds()).stream()
            .collect(Collectors.toMap(LabTest::getId, Function.identity()));
    var ordered =
        req.testIds().stream()
            .map(
                tid -> {
                  var t = found.get(tid);
                  if (t == null) {
                    throw new BadRequestException(
                        "LAB_TEST_NOT_FOUND", "Um dos exames não existe no catálogo.");
                  }
                  if (!t.isActive()) {
                    throw new ConflictException(
                        "LAB_TEST_INACTIVE", "O exame " + t.getName() + " está desactivado.");
                  }
                  return t;
                })
            .toList();
    var today = LocalDate.ofInstant(clock.instant(), app.timeZone());
    var r =
        requests.saveAndFlush(
            new LabRequest(
                numbers.next(user.hospitalId(), today.getYear()),
                episode.patientId(),
                episode.id(),
                user.id(),
                clock.instant(),
                req.priority(),
                optional(req.clinicalInfo()),
                ordered));
    var response = toResponse(r, context(List.of(r), true));
    audit.record(
        entry(
            AuditAction.CREATE,
            r,
            "Exames pedidos: "
                + describe(response)
                + " — "
                + r.getItems().stream()
                    .map(LabRequestItem::getTestName)
                    .collect(Collectors.joining(", "))));
    return response;
  }

  @Transactional
  public LabRequestResponse collect(UUID id, String notes) {
    var user = currentUser.get();
    var r = lock(id);
    r.collect(user.id(), clock.instant(), optional(notes));
    return save(r, AuditAction.SAMPLE_COLLECTED, "Amostra colhida: ");
  }

  @Transactional
  public LabRequestResponse startAnalysis(UUID id) {
    var r = lock(id);
    r.startAnalysis(clock.instant());
    return save(r, AuditAction.START, "Análise iniciada: ");
  }

  @Transactional
  public LabRequestResponse recordResult(UUID id, UUID itemId, ResultRequest req) {
    var user = currentUser.get();
    var r = lock(id);
    var item =
        r.recordResult(
            itemId,
            new ResultValue(
                req.numericValue(),
                req.textValue(),
                Boolean.TRUE.equals(req.abnormal()),
                optional(req.notes())),
            user.id(),
            clock.instant());
    return save(
        r,
        AuditAction.RESULT_RECORDED,
        "Resultado registado (" + item.getTestName() + " = " + display(item) + "): ");
  }

  @Transactional
  public LabRequestResponse validate(UUID id, UUID itemId) {
    var user = currentUser.get();
    var r = lock(id);
    var item = r.validate(itemId, user.id(), clock.instant());
    return save(
        r,
        AuditAction.RESULT_VALIDATED,
        "Resultado validado (" + item.getTestName() + " = " + display(item) + "): ");
  }

  @Transactional
  public LabRequestResponse amend(UUID id, UUID itemId, AmendRequest req) {
    var user = currentUser.get();
    var r = lock(id);
    var before = display(r.item(itemId));
    ResultAmendment amendment =
        r.amend(
            itemId,
            new ResultValue(
                req.numericValue(),
                req.textValue(),
                Boolean.TRUE.equals(req.abnormal()),
                optional(req.notes())),
            req.reason().strip(),
            user.id(),
            clock.instant());
    amendments.save(amendment);
    var item = r.item(itemId);
    return save(
        r,
        AuditAction.RESULT_AMENDED,
        "Resultado corrigido ("
            + item.getTestName()
            + ": "
            + before
            + " → "
            + display(item)
            + "; motivo: "
            + req.reason().strip()
            + "): ");
  }

  @Transactional
  public LabRequestResponse cancel(UUID id, String reason) {
    var user = currentUser.get();
    var r = lock(id);
    r.cancel(reason.strip(), user.id(), clock.instant());
    return save(r, AuditAction.CANCEL, "Pedido cancelado (" + reason.strip() + "): ");
  }

  // ---------- Auxiliares ----------

  private LabRequestResponse save(LabRequest r, AuditAction action, String prefix) {
    var saved = requests.saveAndFlush(r);
    var response = toResponse(saved, context(List.of(saved), true));
    audit.record(entry(action, saved, prefix + describe(response)));
    return response;
  }

  private LabRequest load(UUID id) {
    return requests
        .findScoped(id)
        .orElseThrow(
            () -> new NotFoundException("LAB_REQUEST_NOT_FOUND", "Pedido não encontrado."));
  }

  private LabRequest lock(UUID id) {
    catalog.requireHospitalUser();
    return requests
        .findForUpdate(id)
        .orElseThrow(
            () -> new NotFoundException("LAB_REQUEST_NOT_FOUND", "Pedido não encontrado."));
  }

  private record Context(
      Map<UUID, PatientRef> patients,
      Map<UUID, String> names,
      Map<UUID, List<ResultAmendment>> amendments,
      boolean seesUnvalidated) {}

  private Context context(List<LabRequest> list, boolean withAmendments) {
    var user = currentUser.get();
    var itemIds =
        list.stream().flatMap(r -> r.getItems().stream()).map(LabRequestItem::getId).toList();
    var amends =
        withAmendments && !itemIds.isEmpty()
            ? amendments.findByItems(itemIds).stream()
                .collect(Collectors.groupingBy(ResultAmendment::getItemId))
            : Map.<UUID, List<ResultAmendment>>of();
    Set<UUID> people = new HashSet<>();
    for (var r : list) {
      Stream.of(r.getRequestedBy(), r.getCollectedBy(), r.getCancelledBy())
          .filter(Objects::nonNull)
          .forEach(people::add);
      r.getItems()
          .forEach(
              i ->
                  Stream.of(i.getResultedBy(), i.getValidatedBy())
                      .filter(Objects::nonNull)
                      .forEach(people::add));
    }
    amends.values().forEach(l -> l.forEach(a -> people.add(a.getAmendedBy())));
    return new Context(
        patients.findAll(list.stream().map(LabRequest::getPatientId).distinct().toList()),
        staff.names(people),
        amends,
        user.hasRole(Role.LAB_TECHNICIAN));
  }

  private LabRequestResponse toResponse(LabRequest r, Context c) {
    var p = c.patients().get(r.getPatientId());
    var items = r.getItems().stream().map(i -> toItem(i, c)).toList();
    boolean abnormal =
        items.stream()
            .anyMatch(i -> i.resultVisible() && i.flag() != null && i.flag() != ResultFlag.NORMAL);
    return new LabRequestResponse(
        r.getId(),
        r.getNumber(),
        r.getPatientId(),
        p == null ? null : p.fullName(),
        p == null ? null : p.recordNumber(),
        r.getEpisodeId(),
        name(c, r.getRequestedBy()),
        r.getRequestedAt(),
        r.getPriority(),
        r.getClinicalInfo(),
        r.getStatus(),
        r.getCollectedAt(),
        name(c, r.getCollectedBy()),
        r.getCollectionNotes(),
        r.getAnalysisStartedAt(),
        r.getCompletedAt(),
        r.getCancelledAt(),
        name(c, r.getCancelledBy()),
        r.getCancellationReason(),
        abnormal,
        items,
        r.getVersion() == null ? 0 : r.getVersion());
  }

  /** Quem não é do laboratório só vê o resultado depois de validado. */
  private ItemResponse toItem(LabRequestItem i, Context c) {
    boolean visible =
        i.getStatus() == ItemStatus.VALIDATED
            || (c.seesUnvalidated() && i.getStatus() == ItemStatus.RESULTED);
    var history =
        c.amendments().getOrDefault(i.getId(), List.of()).stream()
            .map(
                a ->
                    new AmendmentView(
                        a.getPreviousNumeric(),
                        a.getPreviousText(),
                        a.getPreviousFlag(),
                        a.getNewNumeric(),
                        a.getNewText(),
                        a.getNewFlag(),
                        a.getReason(),
                        name(c, a.getAmendedBy()),
                        a.getAmendedAt()))
            .toList();
    return new ItemResponse(
        i.getId(),
        i.getLabTestId(),
        i.getTestCode(),
        i.getTestName(),
        i.getResultType(),
        i.getUnit(),
        i.getReferenceLow(),
        i.getReferenceHigh(),
        i.getReferenceText(),
        i.getStatus(),
        visible,
        visible ? i.getNumericValue() : null,
        visible ? i.getTextValue() : null,
        visible ? i.getFlag() : null,
        visible ? i.getResultNotes() : null,
        i.getResultedAt(),
        name(c, i.getResultedBy()),
        i.getValidatedAt(),
        name(c, i.getValidatedBy()),
        i.isAmended(),
        history);
  }

  private static String name(Context c, UUID id) {
    return id == null ? null : c.names().get(id);
  }

  private static String display(LabRequestItem i) {
    var v =
        i.getNumericValue() != null
            ? i.getNumericValue().stripTrailingZeros().toPlainString()
                + (i.getUnit() == null ? "" : " " + i.getUnit())
            : i.getTextValue();
    return v == null
        ? "—"
        : v
            + (i.getFlag() == null || i.getFlag() == ResultFlag.NORMAL
                ? ""
                : " [" + i.getFlag() + "]");
  }

  private AuditEntry entry(AuditAction action, LabRequest r, String summary) {
    return AuditEntry.success(
        action, AuditEntityType.LAB_REQUEST, r.getId(), r.getHospitalId(), summary);
  }

  private static String describe(LabRequestResponse r) {
    return r.number() + " — " + r.patientName() + " (processo " + r.patientRecordNumber() + ")";
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
