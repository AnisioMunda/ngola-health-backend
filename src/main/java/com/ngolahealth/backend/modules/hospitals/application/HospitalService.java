package com.ngolahealth.backend.modules.hospitals.application;

import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.hospitals.api.HospitalRequest;
import com.ngolahealth.backend.modules.hospitals.api.HospitalResponse;
import com.ngolahealth.backend.modules.hospitals.domain.Hospital;
import com.ngolahealth.backend.modules.hospitals.domain.HospitalDetails;
import com.ngolahealth.backend.modules.hospitals.domain.HospitalRepository;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso da gestão de hospitais. Ponto de entrada dos outros módulos para hospitais. */
@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalService {

  static final String NOT_FOUND = "HOSPITAL_NOT_FOUND";
  static final String CODE_IN_USE = "HOSPITAL_CODE_IN_USE";
  static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";

  private final HospitalRepository repository;

  @Transactional(readOnly = true)
  public List<HospitalResponse> findActive() {
    return repository.findByActiveTrueOrderByNameAsc().stream()
        .map(HospitalResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public PageResponse<HospitalResponse> search(String search, Boolean active, int page, int size) {
    var pageable = PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("code")));
    return PageResponse.of(
        repository.search(likePattern(search), active, pageable), HospitalResponse::from);
  }

  /** Resumo para outros módulos (ex.: validar o hospital de um utilizador). */
  @Transactional(readOnly = true)
  public Optional<HospitalSummary> findSummary(UUID id) {
    return repository.findById(id).map(HospitalSummary::from);
  }

  /** Nomes dos hospitais indicados, para enriquecer listagens de outros módulos. */
  @Transactional(readOnly = true)
  public Map<UUID, String> findNames(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return repository.findAllById(ids).stream()
        .collect(Collectors.toMap(Hospital::getId, Hospital::getName));
  }

  @Transactional(readOnly = true)
  public HospitalResponse findById(UUID id) {
    return HospitalResponse.from(load(id));
  }

  @Transactional
  public HospitalResponse create(HospitalRequest request) {
    var details = normalize(request);
    if (repository.existsByCode(details.code())) {
      throw codeInUse(details.code());
    }
    var saved = repository.save(new Hospital(details));
    log.info("Hospital registado: id={} código={}", saved.getId(), saved.getCode());
    return HospitalResponse.from(saved);
  }

  @Transactional
  public HospitalResponse update(UUID id, HospitalRequest request) {
    var hospital = load(id);
    if (request.version() != null && request.version() != hospital.getVersion()) {
      throw new ConflictException(
          CONCURRENT_MODIFICATION,
          "O hospital foi alterado por outro utilizador. Actualize e tente novamente.");
    }
    var details = normalize(request);
    if (repository.existsByCodeAndIdNot(details.code(), id)) {
      throw codeInUse(details.code());
    }
    hospital.apply(details);
    var saved = repository.saveAndFlush(hospital);
    log.info("Hospital actualizado: id={}", id);
    return HospitalResponse.from(saved);
  }

  @Transactional
  public HospitalResponse setActive(UUID id, boolean active) {
    var hospital = load(id);
    if (hospital.isActive() != active) {
      if (active) {
        hospital.activate();
      } else {
        hospital.deactivate();
      }
      hospital = repository.saveAndFlush(hospital);
      log.info("Hospital {}: id={}", active ? "activado" : "desactivado", id);
    }
    return HospitalResponse.from(hospital);
  }

  private Hospital load(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND, "Hospital não encontrado."));
  }

  private static ConflictException codeInUse(String code) {
    return new ConflictException(CODE_IN_USE, "Já existe um hospital com o código " + code + ".");
  }

  static HospitalDetails normalize(HospitalRequest r) {
    return new HospitalDetails(
        r.name().strip(),
        r.code().strip().toUpperCase(Locale.ROOT),
        r.type(),
        r.province().strip(),
        optional(r.municipality()),
        optional(r.address()),
        optional(r.phone()),
        lower(optional(r.email())),
        upper(optional(r.taxId())));
  }

  /** Converte o termo de pesquisa num padrão LIKE seguro (ou null se vazio). */
  static String likePattern(String search) {
    var term = optional(search);
    if (term == null) {
      return null;
    }
    var escaped =
        term.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String lower(String value) {
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }

  private static String upper(String value) {
    return value == null ? null : value.toUpperCase(Locale.ROOT);
  }
}
