package com.ngolahealth.backend.modules.users.application;

import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.modules.users.domain.SessionRevocationReason;
import com.ngolahealth.backend.modules.users.domain.User;
import com.ngolahealth.backend.modules.users.domain.UserRepository;
import com.ngolahealth.backend.modules.users.domain.UserSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contas do portal do paciente, para o módulo do portal (este módulo é o único que grava
 * utilizadores).
 */
@Service
@RequiredArgsConstructor
public class PortalAccounts {

  private final UserRepository users;
  private final UserSessionRepository sessions;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public record AccountInfo(
      UUID id, String username, String email, boolean active, Instant lastLoginAt) {}

  @Transactional(readOnly = true)
  public Optional<AccountInfo> find(UUID patientId) {
    return users.findByPatientId(patientId).map(PortalAccounts::info);
  }

  /** Cria a conta do doente (senha escolhida por ele). Nome de utilizador e correio são únicos. */
  @Transactional
  public AccountInfo create(
      UUID hospitalId,
      UUID patientId,
      String fullName,
      String username,
      String email,
      String rawPassword) {
    if (users.findByPatientId(patientId).isPresent()) {
      throw new ConflictException("PORTAL_ACCOUNT_EXISTS", "Este doente já tem conta no portal.");
    }
    if (users.existsByUsername(username)) {
      throw new ConflictException(
          "USERNAME_TAKEN", "Esse nome de utilizador já está em uso. Escolha outro.");
    }
    if (users.existsByEmail(email)) {
      throw new ConflictException(
          "EMAIL_TAKEN", "Esse correio electrónico já está associado a uma conta.");
    }
    var user =
        users.saveAndFlush(
            User.forPatient(
                username,
                passwordEncoder.encode(rawPassword),
                fullName,
                email,
                hospitalId,
                patientId,
                clock.instant()));
    return info(user);
  }

  /** Desactivar termina já todas as sessões do doente. */
  @Transactional
  public AccountInfo setActive(UUID patientId, boolean active) {
    var user =
        users
            .findByPatientId(patientId)
            .orElseThrow(
                () ->
                    new ConflictException(
                        "NO_PORTAL_ACCOUNT", "O doente não tem conta no portal."));
    if (active) {
      user.activate();
    } else {
      user.deactivate();
      sessions.revokeAll(
          user.getId(), null, clock.instant(), SessionRevocationReason.USER_DEACTIVATED.name());
    }
    return info(users.saveAndFlush(user));
  }

  private static AccountInfo info(User u) {
    return new AccountInfo(
        u.getId(), u.getUsername(), u.getEmail(), u.isActive(), u.getLastLoginAt());
  }
}
