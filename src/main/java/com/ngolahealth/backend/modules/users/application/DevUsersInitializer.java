package com.ngolahealth.backend.modules.users.application;

import com.ngolahealth.backend.modules.hospitals.application.HospitalService;
import com.ngolahealth.backend.modules.users.domain.User;
import com.ngolahealth.backend.modules.users.domain.UserProfile;
import com.ngolahealth.backend.modules.users.domain.UserRepository;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.security.password.PasswordPolicyValidator;
import java.util.EnumSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil dev: cria os utilizadores de demonstração se não existirem, com a senha de {@code
 * DEV_ADMIN_PASSWORD} (troca obrigatória no primeiro acesso). Feito em Java porque o MySQL não gera
 * hashes BCrypt.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevUsersInitializer implements ApplicationRunner {

  static final UUID DEMO_HOSPITAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");

  private final UserRepository users;
  private final HospitalService hospitals;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.dev.admin-password:}")
  private String password;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (password == null || password.isBlank()) {
      log.warn("DEV_ADMIN_PASSWORD vazio: utilizadores de demonstração não criados.");
      return;
    }
    if (!PasswordPolicyValidator.isStrong(password)) {
      log.warn(
          "DEV_ADMIN_PASSWORD não cumpre a política de senhas (10+ caracteres, maiúscula,"
              + " minúscula e número): utilizadores de demonstração não criados.");
      return;
    }
    create(
        "platform-admin",
        "Administrador da Plataforma",
        "platform-admin@ngolahealth.local",
        null,
        Role.SUPER_ADMIN);
    if (hospitals.findSummary(DEMO_HOSPITAL_ID).isPresent()) {
      create(
          "admin.hcl",
          "Administrador do HCL",
          "admin.hcl@ngolahealth.local",
          DEMO_HOSPITAL_ID,
          Role.ADMIN);
    }
  }

  private void create(String username, String name, String email, UUID hospitalId, Role role) {
    if (users.existsByUsername(username)) {
      return;
    }
    users.save(
        new User(
            username,
            passwordEncoder.encode(password),
            new UserProfile(name, email, null, null, null),
            hospitalId,
            EnumSet.of(role)));
    log.info("Utilizador de demonstração criado: {} ({})", username, role);
  }
}
