package com.ngolahealth.backend.modules.inpatient.application;

import com.ngolahealth.backend.modules.inpatient.domain.Ward;
import com.ngolahealth.backend.modules.inpatient.domain.WardRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Acesso de outros módulos às enfermarias do hospital (ex.: turnos por enfermaria). */
@Service
@RequiredArgsConstructor
public class WardDirectory {

  private final WardRepository wards;

  @Transactional(readOnly = true)
  public Optional<String> activeName(UUID id) {
    return wards.findScoped(id).filter(Ward::isActive).map(Ward::getName);
  }

  @Transactional(readOnly = true)
  public Map<UUID, String> names(Collection<UUID> ids) {
    return ids.isEmpty()
        ? java.util.Collections.emptyMap()
        : wards.findAllScoped(ids).stream().collect(Collectors.toMap(Ward::getId, Ward::getName));
  }
}
