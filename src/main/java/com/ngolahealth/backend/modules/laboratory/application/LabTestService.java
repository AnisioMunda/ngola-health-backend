package com.ngolahealth.backend.modules.laboratory.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.LabTestRequest;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.LabTestResponse;
import com.ngolahealth.backend.modules.laboratory.domain.LabTest;
import com.ngolahealth.backend.modules.laboratory.domain.LabTestDetails;
import com.ngolahealth.backend.modules.laboratory.domain.LabTestRepository;
import com.ngolahealth.backend.modules.laboratory.domain.ResultType;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo de exames do laboratório. */
@Service
@RequiredArgsConstructor
public class LabTestService {

  private final LabTestRepository tests;
  private final CurrentUserProvider currentUser;
  private final AuditTrail audit;

  @Transactional(readOnly = true)
  public List<LabTestResponse> search(String search, Boolean active) {
    requireHospitalUser();
    var term = search == null || search.isBlank() ? null : "%" + escape(search.strip()) + "%";
    return tests.search(term, active).stream().map(LabTestService::toResponse).toList();
  }

  @Transactional
  public LabTestResponse create(LabTestRequest r) {
    requireHospitalUser();
    var details = normalize(r);
    if (tests.codeTaken(details.code(), null)) {
      throw new ConflictException(
          "LAB_TEST_CODE_IN_USE", "Já existe um exame com o código " + details.code() + ".");
    }
    var test = tests.saveAndFlush(new LabTest(details));
    var response = toResponse(test);
    audit.record(
        AuditEntry.success(
                AuditAction.CREATE,
                AuditEntityType.LAB_TEST,
                test.getId(),
                test.getHospitalId(),
                "Exame adicionado ao catálogo: " + test.getCode() + " — " + test.getName())
            .withChanges(audit.snapshot(response)));
    return response;
  }

  @Transactional
  public LabTestResponse update(UUID id, LabTestRequest r) {
    requireHospitalUser();
    var test = load(id);
    if (r.version() != null && r.version() != test.getVersion()) {
      throw new ConflictException(
          "CONCURRENT_MODIFICATION",
          "O exame foi alterado por outra pessoa. Actualize e tente novamente.");
    }
    var details = normalize(r);
    if (tests.codeTaken(details.code(), id)) {
      throw new ConflictException(
          "LAB_TEST_CODE_IN_USE", "Já existe um exame com o código " + details.code() + ".");
    }
    var before = toResponse(test);
    test.apply(details);
    var after = toResponse(tests.saveAndFlush(test));
    audit.record(
        AuditEntry.success(
                AuditAction.UPDATE,
                AuditEntityType.LAB_TEST,
                id,
                test.getHospitalId(),
                "Exame editado: " + test.getCode() + " — " + test.getName())
            .withChanges(audit.diff(before, after)));
    return after;
  }

  @Transactional
  public LabTestResponse setActive(UUID id, boolean active) {
    requireHospitalUser();
    var test = load(id);
    if (test.isActive() != active) {
      if (active) {
        test.activate();
      } else {
        test.deactivate();
      }
      test = tests.saveAndFlush(test);
      audit.record(
          AuditEntry.success(
              active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE,
              AuditEntityType.LAB_TEST,
              id,
              test.getHospitalId(),
              "Exame " + (active ? "reactivado: " : "desactivado: ") + test.getName()));
    } else {
      audit.markHandled();
    }
    return toResponse(test);
  }

  LabTest load(UUID id) {
    return tests
        .findScoped(id)
        .orElseThrow(() -> new NotFoundException("LAB_TEST_NOT_FOUND", "Exame não encontrado."));
  }

  void requireHospitalUser() {
    if (currentUser.get().hospitalId() == null) {
      throw new ForbiddenException(
          "HOSPITAL_SCOPE_REQUIRED",
          "O laboratório só está acessível a utilizadores de um hospital.");
    }
  }

  static LabTestDetails normalize(LabTestRequest r) {
    boolean numeric = r.resultType() == ResultType.NUMERIC;
    if (numeric
        && r.referenceLow() != null
        && r.referenceHigh() != null
        && r.referenceLow().compareTo(r.referenceHigh()) > 0) {
      throw new BadRequestException(
          "INVALID_REFERENCE_RANGE", "O limite inferior não pode ser maior do que o superior.");
    }
    return new LabTestDetails(
        r.code().strip().toUpperCase(Locale.ROOT),
        r.name().strip(),
        r.category(),
        r.sampleType(),
        r.resultType(),
        optional(r.unit()),
        numeric ? r.referenceLow() : null,
        numeric ? r.referenceHigh() : null,
        optional(r.referenceText()),
        r.turnaroundHours());
  }

  static LabTestResponse toResponse(LabTest t) {
    return new LabTestResponse(
        t.getId(),
        t.getCode(),
        t.getName(),
        t.getCategory(),
        t.getSampleType(),
        t.getResultType(),
        t.getUnit(),
        t.getReferenceLow(),
        t.getReferenceHigh(),
        t.getReferenceText(),
        t.getTurnaroundHours(),
        t.isActive(),
        t.getVersion());
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
