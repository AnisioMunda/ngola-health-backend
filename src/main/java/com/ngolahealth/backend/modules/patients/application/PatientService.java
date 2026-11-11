package com.ngolahealth.backend.modules.patients.application;

import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.patients.api.PatientDtos.DuplicateCheckRequest;
import com.ngolahealth.backend.modules.patients.api.PatientDtos.PatientRequest;
import com.ngolahealth.backend.modules.patients.api.PatientDtos.PatientResponse;
import com.ngolahealth.backend.modules.patients.api.PatientDtos.PatientSummary;
import com.ngolahealth.backend.modules.patients.domain.Patient;
import com.ngolahealth.backend.modules.patients.domain.PatientDetails;
import com.ngolahealth.backend.modules.patients.domain.PatientRecordNumbers;
import com.ngolahealth.backend.modules.patients.domain.PatientRepository;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de pacientes. O isolamento por hospital é automático (Hibernate); aqui garante-se apenas
 * que o utilizador pertence a um hospital, com uma mensagem clara.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

  static final String NOT_FOUND = "PATIENT_NOT_FOUND";
  static final String IDENTIFIER_IN_USE = "PATIENT_IDENTIFIER_IN_USE";

  private final PatientRepository patients;
  private final PatientRecordNumbers recordNumbers;
  private final CurrentUserProvider currentUser;
  private final AuditTrail audit;
  private final Clock clock;

  @Transactional(readOnly = true)
  public PageResponse<PatientSummary> search(String search, Boolean active, int page, int size) {
    requireHospitalUser();
    var result =
        patients.search(
            likePattern(search),
            active,
            PageRequest.of(page, size, Sort.by("fullName").and(Sort.by("recordNumber"))));
    var today = today();
    return PageResponse.of(result, p -> toSummary(p, today));
  }

  /** Abre a ficha do paciente; a consulta fica registada na auditoria. */
  @Transactional
  public PatientResponse view(UUID id) {
    requireHospitalUser();
    var patient = load(id);
    audit.record(
        AuditEntry.success(
            AuditAction.VIEW,
            AuditEntityType.PATIENT,
            id,
            patient.getHospitalId(),
            "Ficha consultada: "
                + patient.getFullName()
                + " (processo "
                + patient.getRecordNumber()
                + ")"));
    return toResponse(patient);
  }

  @Transactional(readOnly = true)
  public List<PatientSummary> findPossibleDuplicates(DuplicateCheckRequest request) {
    requireHospitalUser();
    var today = today();
    return patients
        .findPossibleDuplicates(
            request.birthDate(),
            namePattern(request.fullName()),
            upper(optional(request.nationalId())),
            optional(request.phone()),
            PageRequest.of(0, 10))
        .stream()
        .map(p -> toSummary(p, today))
        .toList();
  }

  @Transactional
  public PatientResponse create(PatientRequest request) {
    var user = requireHospitalUser();
    var details = normalize(request);
    ensureIdentifiersFree(details, null);
    var patient =
        patients.saveAndFlush(
            new Patient(recordNumbers.next(user.hospitalId()), details, user.id()));
    var response = toResponse(patient);
    audit.record(
        AuditEntry.success(
                AuditAction.CREATE,
                AuditEntityType.PATIENT,
                patient.getId(),
                patient.getHospitalId(),
                "Paciente registado: "
                    + patient.getFullName()
                    + " (processo "
                    + patient.getRecordNumber()
                    + ")")
            .withChanges(audit.snapshot(response)));
    log.info("Paciente registado: processo {}", patient.getRecordNumber());
    return response;
  }

  @Transactional
  public PatientResponse update(UUID id, PatientRequest request) {
    requireHospitalUser();
    var patient = load(id);
    if (request.version() != null && request.version() != patient.getVersion()) {
      throw new ConflictException(
          "CONCURRENT_MODIFICATION",
          "A ficha foi alterada por outra pessoa. Actualize e tente novamente.");
    }
    var details = normalize(request);
    ensureIdentifiersFree(details, id);
    var before = toResponse(patient);
    patient.apply(details);
    var after = toResponse(patients.saveAndFlush(patient));
    audit.record(
        AuditEntry.success(
                AuditAction.UPDATE,
                AuditEntityType.PATIENT,
                id,
                patient.getHospitalId(),
                "Ficha editada: " + after.fullName() + " (processo " + after.recordNumber() + ")")
            .withChanges(audit.diff(before, after)));
    return after;
  }

  @Transactional
  public PatientResponse setActive(UUID id, boolean active) {
    requireHospitalUser();
    var patient = load(id);
    if (patient.isActive() != active) {
      if (active) {
        patient.activate();
      } else {
        patient.deactivate();
      }
      patient = patients.saveAndFlush(patient);
      audit.record(
          AuditEntry.success(
              active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE,
              AuditEntityType.PATIENT,
              id,
              patient.getHospitalId(),
              "Paciente " + (active ? "reactivado: " : "inactivado: ") + patient.getFullName()));
    } else {
      audit.markHandled();
    }
    return toResponse(patient);
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

  private Patient load(UUID id) {
    return patients
        .findScoped(id)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND, "Paciente não encontrado."));
  }

  private void ensureIdentifiersFree(PatientDetails d, UUID ignoreId) {
    if (d.nationalId() != null
        && (ignoreId == null
            ? patients.existsByNationalId(d.nationalId())
            : patients.existsByNationalIdAndIdNot(d.nationalId(), ignoreId))) {
      throw new ConflictException(
          IDENTIFIER_IN_USE, "Já existe um paciente com o BI/NIF " + d.nationalId() + ".");
    }
    if (d.healthCardNumber() != null
        && (ignoreId == null
            ? patients.existsByHealthCardNumber(d.healthCardNumber())
            : patients.existsByHealthCardNumberAndIdNot(d.healthCardNumber(), ignoreId))) {
      throw new ConflictException(
          IDENTIFIER_IN_USE,
          "Já existe um paciente com o cartão de saúde " + d.healthCardNumber() + ".");
    }
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private PatientResponse toResponse(Patient p) {
    return new PatientResponse(
        p.getId(),
        p.getRecordNumber(),
        p.getFullName(),
        p.getBirthDate(),
        p.ageOn(today()),
        p.getGender(),
        p.getNationalId(),
        p.getHealthCardNumber(),
        p.getPhone(),
        p.getEmail(),
        p.getAddress(),
        p.getProvince(),
        p.getMunicipality(),
        p.getEmergencyContactName(),
        p.getEmergencyContactPhone(),
        p.getEmergencyContactRelationship(),
        p.getBloodType(),
        p.getAllergies(),
        p.getChronicConditions(),
        p.getNotes(),
        p.isActive(),
        p.getVersion(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }

  private static PatientSummary toSummary(Patient p, LocalDate today) {
    return new PatientSummary(
        p.getId(),
        p.getRecordNumber(),
        p.getFullName(),
        p.getBirthDate(),
        p.ageOn(today),
        p.getGender(),
        p.getNationalId(),
        p.getPhone(),
        p.isActive());
  }

  static PatientDetails normalize(PatientRequest r) {
    return new PatientDetails(
        collapseSpaces(r.fullName()),
        r.birthDate(),
        r.gender(),
        upper(optional(r.nationalId())),
        optional(r.healthCardNumber()),
        optional(r.phone()),
        lower(optional(r.email())),
        optional(r.address()),
        optional(r.province()),
        optional(r.municipality()),
        optional(r.emergencyContactName()),
        optional(r.emergencyContactPhone()),
        optional(r.emergencyContactRelationship()),
        upper(optional(r.bloodType())),
        optional(r.allergies()),
        optional(r.chronicConditions()),
        optional(r.notes()));
  }

  /** "Ana Maria Silva" → "%Ana%Silva%": primeiro e último nome, para apanhar nomes do meio. */
  static String namePattern(String fullName) {
    var parts = escape(collapseSpaces(fullName)).split(" ");
    return parts.length == 1
        ? "%" + parts[0] + "%"
        : "%" + parts[0] + "%" + parts[parts.length - 1] + "%";
  }

  static String likePattern(String search) {
    var term = optional(search);
    return term == null ? null : "%" + escape(term) + "%";
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static String collapseSpaces(String value) {
    return String.join(" ", Arrays.stream(value.strip().split("\\s+")).toList());
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String upper(String value) {
    return value == null ? null : value.toUpperCase(Locale.ROOT);
  }

  private static String lower(String value) {
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }
}
