package com.ngolahealth.backend.modules.users.application;

import com.ngolahealth.backend.modules.users.domain.User;
import com.ngolahealth.backend.modules.users.domain.UserRepository;
import com.ngolahealth.backend.security.Role;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Acesso de outros módulos aos profissionais de um hospital. */
@Service
@RequiredArgsConstructor
public class StaffDirectory {

  private final UserRepository users;

  public record StaffRef(UUID id, String fullName, String specialty) {

    static StaffRef of(User u) {
      return new StaffRef(u.getId(), u.getFullName(), u.getSpecialty());
    }
  }

  /** Profissional activo do hospital indicado com o perfil pedido. */
  @Transactional(readOnly = true)
  public Optional<StaffRef> findActive(UUID userId, UUID hospitalId, Role role) {
    return users
        .findById(userId)
        .filter(
            u -> u.isActive() && Objects.equals(u.getHospitalId(), hospitalId) && u.hasRole(role))
        .map(StaffRef::of);
  }

  @Transactional(readOnly = true)
  public List<StaffRef> listActive(UUID hospitalId, Role role) {
    return users.findActiveByHospitalAndRole(hospitalId, role).stream().map(StaffRef::of).toList();
  }

  /** Nomes de utilizadores (de qualquer estado), para mostrar autores e responsáveis. */
  @Transactional(readOnly = true)
  public Map<UUID, String> names(Collection<UUID> ids) {
    var wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    if (wanted.isEmpty()) {
      return Map.of();
    }
    return users.findAllById(wanted).stream()
        .collect(Collectors.toMap(User::getId, User::getFullName));
  }
}
