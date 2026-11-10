package com.ngolahealth.backend.modules.users.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.hospitals.application.HospitalService;
import com.ngolahealth.backend.modules.users.api.UserDtos.CreateUserRequest;
import com.ngolahealth.backend.modules.users.api.UserDtos.UpdateUserRequest;
import com.ngolahealth.backend.modules.users.api.UserDtos.UserResponse;
import com.ngolahealth.backend.modules.users.domain.SessionRevocationReason;
import com.ngolahealth.backend.modules.users.domain.User;
import com.ngolahealth.backend.modules.users.domain.UserProfile;
import com.ngolahealth.backend.modules.users.domain.UserRepository;
import com.ngolahealth.backend.modules.users.domain.UserSessionRepository;
import com.ngolahealth.backend.modules.users.domain.UserStatus;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.CurrentUserProvider;
import com.ngolahealth.backend.security.Role;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de utilizadores. O SUPER_ADMIN gere todos; ADMIN e MANAGER só vêem o próprio hospital (um
 * utilizador de outro hospital responde 404, para não revelar a sua existência).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  static final String NOT_FOUND = "USER_NOT_FOUND";

  private final UserRepository users;
  private final UserSessionRepository sessions;
  private final HospitalService hospitals;
  private final PasswordEncoder passwordEncoder;
  private final CurrentUserProvider currentUser;
  private final Clock clock;

  @Transactional(readOnly = true)
  public PageResponse<UserResponse> search(
      String search, Role role, UserStatus status, UUID hospitalId, int page, int size) {
    var actor = currentUser.get();
    UUID scope = actor.isSuperAdmin() ? hospitalId : actor.hospitalId();
    var result =
        users.search(
            scope,
            status,
            role,
            likePattern(search),
            PageRequest.of(page, size, Sort.by("fullName").and(Sort.by("username"))));
    var names =
        hospitals.findNames(
            result.getContent().stream()
                .map(User::getHospitalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    return PageResponse.of(result, u -> toResponse(u, names));
  }

  @Transactional(readOnly = true)
  public UserResponse findById(UUID id) {
    return toResponse(loadVisible(id, currentUser.get()));
  }

  @Transactional
  public UserResponse create(CreateUserRequest request) {
    var actor = currentUser.get();
    String username = request.username().strip().toLowerCase(Locale.ROOT);
    var profile =
        profile(
            request.fullName(),
            request.email(),
            request.phone(),
            request.specialty(),
            request.professionalCardNumber());
    var roles = EnumSet.copyOf(request.roles());
    UUID hospitalId = resolveAssignment(actor, roles, request.hospitalId(), null);

    if (users.existsByUsername(username)) {
      throw new ConflictException(
          "USERNAME_IN_USE", "O nome de utilizador " + username + " já está em uso.");
    }
    ensureEmailFree(profile.email(), null);

    var user =
        users.save(
            new User(
                username,
                passwordEncoder.encode(request.temporaryPassword()),
                profile,
                hospitalId,
                roles));
    log.info("Utilizador criado: {} por {}", username, actor.username());
    return toResponse(user);
  }

  @Transactional
  public UserResponse update(UUID id, UpdateUserRequest request) {
    var actor = currentUser.get();
    var user = loadVisible(id, actor);
    if (request.version() != null && request.version() != user.getVersion()) {
      throw new ConflictException(
          "CONCURRENT_MODIFICATION",
          "O utilizador foi alterado por outra pessoa. Actualize e tente novamente.");
    }
    var profile =
        profile(
            request.fullName(),
            request.email(),
            request.phone(),
            request.specialty(),
            request.professionalCardNumber());
    var roles = EnumSet.copyOf(request.roles());
    UUID hospitalId = resolveAssignment(actor, roles, request.hospitalId(), user.getHospitalId());
    if (user.getId().equals(actor.id()) && !roles.containsAll(adminRolesOf(actor))) {
      throw new BadRequestException(
          "CANNOT_REMOVE_OWN_ADMIN_ROLE",
          "Não pode retirar o seu próprio perfil de administração.");
    }
    if (user.hasRole(Role.SUPER_ADMIN) != roles.contains(Role.SUPER_ADMIN)) {
      throw new BadRequestException(
          "SUPER_ADMIN_ROLE_IMMUTABLE",
          "Não é possível converter um administrador da plataforma num utilizador de hospital, "
              + "nem o contrário. Crie uma conta nova.");
    }
    ensureEmailFree(profile.email(), user.getId());

    user.apply(profile);
    user.assign(hospitalId, roles);
    var saved = users.saveAndFlush(user);
    log.info("Utilizador actualizado: {} por {}", user.getUsername(), actor.username());
    return toResponse(saved);
  }

  @Transactional
  public UserResponse setActive(UUID id, boolean active) {
    var actor = currentUser.get();
    var user = loadVisible(id, actor);
    if (!active && user.getId().equals(actor.id())) {
      throw new BadRequestException(
          "CANNOT_DEACTIVATE_SELF", "Não pode desactivar a sua própria conta.");
    }
    if (user.isActive() != active) {
      if (active) {
        user.activate();
      } else {
        user.deactivate();
        sessions.revokeAll(
            user.getId(), null, clock.instant(), SessionRevocationReason.USER_DEACTIVATED.name());
      }
      user = users.saveAndFlush(user);
      log.info(
          "Utilizador {}: {} por {}",
          active ? "activado" : "desactivado",
          user.getUsername(),
          actor.username());
    }
    return toResponse(user);
  }

  /** Define uma senha temporária, desbloqueia a conta e termina as sessões abertas. */
  @Transactional
  public UserResponse resetPassword(UUID id, String temporaryPassword) {
    var actor = currentUser.get();
    var user = loadVisible(id, actor);
    var now = clock.instant();
    user.resetPassword(passwordEncoder.encode(temporaryPassword), now);
    var saved = users.saveAndFlush(user);
    sessions.revokeAll(user.getId(), null, now, SessionRevocationReason.PASSWORD_RESET.name());
    log.info("Senha reposta: {} por {}", user.getUsername(), actor.username());
    return toResponse(saved);
  }

  /**
   * Valida a combinação papéis + hospital e devolve o hospital a gravar. Regras: SUPER_ADMIN é
   * papel exclusivo, sem hospital, e só atribuível por outro SUPER_ADMIN; os restantes exigem um
   * hospital (o do ADMIN que actua, ou o escolhido pelo SUPER_ADMIN), que tem de estar activo,
   * salvo se for o hospital onde o utilizador editado já está.
   *
   * @param currentHospital hospital actual do utilizador editado (null na criação)
   */
  private UUID resolveAssignment(
      CurrentUser actor, Set<Role> roles, UUID requestedHospital, UUID currentHospital) {
    if (roles.contains(Role.PATIENT)) {
      throw new BadRequestException(
          "INVALID_ROLE", "O perfil de paciente é gerido pelo portal do paciente.");
    }
    if (roles.contains(Role.SUPER_ADMIN)) {
      if (!actor.isSuperAdmin()) {
        throw new ForbiddenException(
            "ACCESS_DENIED", "Só um administrador da plataforma pode atribuir este perfil.");
      }
      if (roles.size() > 1) {
        throw new BadRequestException(
            "INVALID_ROLE",
            "O perfil de administrador da plataforma não pode ser combinado com outros.");
      }
      return null;
    }
    UUID hospitalId = actor.isSuperAdmin() ? requestedHospital : actor.hospitalId();
    if (hospitalId == null) {
      throw new BadRequestException("HOSPITAL_REQUIRED", "Seleccione o hospital do utilizador.");
    }
    var hospital =
        hospitals
            .findSummary(hospitalId)
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "HOSPITAL_NOT_FOUND", "O hospital indicado não existe."));
    if (!hospital.active() && !hospitalId.equals(currentHospital)) {
      throw new BadRequestException("HOSPITAL_INACTIVE", "O hospital indicado está inactivo.");
    }
    return hospitalId;
  }

  private static Set<Role> adminRolesOf(CurrentUser actor) {
    var admin = EnumSet.noneOf(Role.class);
    for (Role r : EnumSet.of(Role.SUPER_ADMIN, Role.ADMIN)) {
      if (actor.hasRole(r)) {
        admin.add(r);
      }
    }
    return admin;
  }

  private User loadVisible(UUID id, CurrentUser actor) {
    return users
        .findById(id)
        .filter(u -> actor.isSuperAdmin() || Objects.equals(u.getHospitalId(), actor.hospitalId()))
        .orElseThrow(() -> new NotFoundException(NOT_FOUND, "Utilizador não encontrado."));
  }

  private void ensureEmailFree(String email, UUID ignoreId) {
    boolean taken =
        ignoreId == null
            ? users.existsByEmail(email)
            : users.existsByEmailAndIdNot(email, ignoreId);
    if (taken) {
      throw new ConflictException(
          "EMAIL_IN_USE", "O correio electrónico " + email + " já está em uso.");
    }
  }

  private UserResponse toResponse(User user) {
    var names =
        user.getHospitalId() == null
            ? Map.<UUID, String>of()
            : hospitals.findNames(Set.of(user.getHospitalId()));
    return toResponse(user, names);
  }

  private static UserResponse toResponse(User u, Map<UUID, String> hospitalNames) {
    return new UserResponse(
        u.getId(),
        u.getUsername(),
        u.getFullName(),
        u.getEmail(),
        u.getPhone(),
        u.getSpecialty(),
        u.getProfessionalCardNumber(),
        u.getRoles().stream().sorted().toList(),
        u.getHospitalId(),
        u.getHospitalId() == null ? null : hospitalNames.get(u.getHospitalId()),
        u.getStatus(),
        u.isMustChangePassword(),
        u.getLockedUntil(),
        u.getLastLoginAt(),
        u.getVersion(),
        u.getCreatedAt(),
        u.getUpdatedAt());
  }

  static UserProfile profile(
      String fullName, String email, String phone, String specialty, String card) {
    return new UserProfile(
        fullName.strip(),
        email.strip().toLowerCase(Locale.ROOT),
        optional(phone),
        optional(specialty),
        optional(card));
  }

  static String likePattern(String search) {
    var term = optional(search);
    if (term == null) {
      return null;
    }
    return "%"
        + term.toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        + "%";
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
