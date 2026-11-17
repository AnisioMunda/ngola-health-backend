package com.ngolahealth.backend.modules.patients.application;

import com.ngolahealth.backend.modules.patients.domain.Patient;
import com.ngolahealth.backend.modules.patients.domain.PatientRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Acesso de outros módulos aos pacientes (sempre no âmbito do hospital do utilizador). */
@Service
@RequiredArgsConstructor
public class PatientDirectory {

  private final PatientRepository patients;

  public record PatientRef(
      UUID id,
      String recordNumber,
      String fullName,
      boolean active,
      String phone,
      com.ngolahealth.backend.modules.patients.domain.Gender gender,
      java.time.LocalDate birthDate) {

    static PatientRef of(Patient p) {
      return new PatientRef(
          p.getId(),
          p.getRecordNumber(),
          p.getFullName(),
          p.isActive(),
          p.getPhone(),
          p.getGender(),
          p.getBirthDate());
    }
  }

  @Transactional(readOnly = true)
  public Optional<PatientRef> find(UUID id) {
    return patients.findScoped(id).map(PatientRef::of);
  }

  @Transactional(readOnly = true)
  public Map<UUID, PatientRef> findAll(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return java.util.Collections.emptyMap();
    }
    return patients.findAllScoped(ids).stream()
        .map(PatientRef::of)
        .collect(Collectors.toMap(PatientRef::id, Function.identity()));
  }
}
