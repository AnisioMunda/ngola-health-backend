package com.ngolahealth.backend.modules.users;

import static com.ngolahealth.backend.support.AuthFixtures.DEFAULT_HOSPITAL;
import static com.ngolahealth.backend.support.AuthFixtures.PASSWORD;
import static com.ngolahealth.backend.support.AuthFixtures.bearerToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ngolahealth.backend.modules.users.domain.UserRepository;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.support.AuthFixtures;
import com.ngolahealth.backend.support.MutableClock;
import com.ngolahealth.backend.support.TestClockConfig;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Fluxo completo de autenticação com tokens reais, sessões e MySQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@Transactional
class AuthApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private MutableClock clock;
  @Autowired private UserRepository users;

  @AfterEach
  void resetClock() {
    clock.reset();
  }

  // ---------- Login ----------

  @Test
  void loginWithUsernameOrEmailReturnsTokensAndProfile() throws Exception {
    fixtures.user("medico.a", DEFAULT_HOSPITAL, Role.DOCTOR);

    var byEmail = fixtures.login(mockMvc, "  MEDICO.A@teste.ao ", PASSWORD);
    assertThat(byEmail.getResponse().getStatus()).isEqualTo(200);

    var body = fixtures.login(mockMvc, "medico.a", PASSWORD).getResponse().getContentAsString();
    assertThat((String) JsonPath.read(body, "$.tokenType")).isEqualTo("Bearer");
    assertThat((Integer) JsonPath.read(body, "$.expiresIn")).isBetween(890, 900);
    assertThat((String) JsonPath.read(body, "$.user.hospitalName"))
        .isEqualTo("Hospital Central de Luanda");
    String token = JsonPath.read(body, "$.accessToken");

    mockMvc
        .perform(get("/auth/me").with(bearerToken(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("medico.a"))
        .andExpect(jsonPath("$.roles[0]").value("DOCTOR"))
        .andExpect(jsonPath("$.mustChangePassword").value(false));
  }

  @Test
  void wrongPasswordAndUnknownUserGiveTheSameAnswer() throws Exception {
    fixtures.user("medico.b", DEFAULT_HOSPITAL, Role.DOCTOR);

    var wrong = fixtures.login(mockMvc, "medico.b", "Errada-12345").getResponse();
    var unknown = fixtures.login(mockMvc, "nao.existe", "Errada-12345").getResponse();

    assertThat(wrong.getStatus()).isEqualTo(401);
    assertThat(unknown.getStatus()).isEqualTo(401);
    assertThat(wrong.getContentAsString()).isEqualTo(unknown.getContentAsString());
    assertThat(wrong.getContentAsString()).contains("INVALID_CREDENTIALS");
  }

  @Test
  void accountLocksAfterFiveFailuresAndUnlocksAfterLockDuration() throws Exception {
    fixtures.user("medico.c", DEFAULT_HOSPITAL, Role.DOCTOR);
    for (int i = 1; i <= 4; i++) {
      assertCode(fixtures.login(mockMvc, "medico.c", "Errada-12345"), 401, "INVALID_CREDENTIALS");
    }
    assertCode(fixtures.login(mockMvc, "medico.c", "Errada-12345"), 401, "ACCOUNT_LOCKED");
    // Bloqueada mesmo com a senha certa.
    assertCode(fixtures.login(mockMvc, "medico.c", PASSWORD), 401, "ACCOUNT_LOCKED");

    clock.advance(Duration.ofMinutes(16));
    assertThat(fixtures.login(mockMvc, "medico.c", PASSWORD).getResponse().getStatus())
        .isEqualTo(200);
    assertThat(users.findByUsername("medico.c").orElseThrow().getFailedLoginAttempts()).isZero();
  }

  @Test
  void inactiveUserAndInactiveHospitalCannotLogin() throws Exception {
    var hospital = fixtures.hospital("HFE-001");
    var user = fixtures.user("medico.d", hospital, Role.DOCTOR);
    fixtures.user("root.d", null, Role.SUPER_ADMIN);
    var root = fixtures.bearer(mockMvc, "root.d");

    mockMvc.perform(patch("/hospitals/" + hospital + "/deactivate").with(root));
    assertCode(fixtures.login(mockMvc, "medico.d", PASSWORD), 401, "HOSPITAL_INACTIVE");

    mockMvc.perform(patch("/hospitals/" + hospital + "/activate").with(root));
    mockMvc.perform(patch("/users/" + user.getId() + "/deactivate").with(root));
    assertCode(fixtures.login(mockMvc, "medico.d", PASSWORD), 401, "ACCOUNT_INACTIVE");
  }

  @Test
  void invalidPayloadIsValidationError() throws Exception {
    mockMvc
        .perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.login").exists())
        .andExpect(jsonPath("$.errors.password").exists());
  }

  // ---------- Tokens e sessões ----------

  @Test
  void garbageOrMissingTokenIsUnauthorized() throws Exception {
    mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/auth/me").with(bearerToken("nao.e.um.jwt")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void refreshRotatesTokenAndDetectsReuse() throws Exception {
    fixtures.user("medico.e", DEFAULT_HOSPITAL, Role.DOCTOR);
    String first = refreshTokenOf(fixtures.login(mockMvc, "medico.e", PASSWORD));

    var rotated = refresh(first);
    assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
    String second = refreshTokenOf(rotated);
    assertThat(second).isNotEqualTo(first);

    // Logo a seguir (outro separador): recusado, mas a sessão continua válida.
    assertCode(refresh(first), 401, "INVALID_REFRESH_TOKEN");
    String third = refreshTokenOf(refresh(second));

    // Mais tarde: reutilização = possível roubo, a sessão inteira é revogada.
    clock.advance(Duration.ofMinutes(2));
    assertCode(refresh(second), 401, "INVALID_REFRESH_TOKEN");
    assertCode(refresh(third), 401, "INVALID_REFRESH_TOKEN");
  }

  @Test
  void refreshTokenExpires() throws Exception {
    fixtures.user("medico.f", DEFAULT_HOSPITAL, Role.DOCTOR);
    String token = refreshTokenOf(fixtures.login(mockMvc, "medico.f", PASSWORD));
    clock.advance(Duration.ofHours(13));
    assertCode(refresh(token), 401, "INVALID_REFRESH_TOKEN");
  }

  @Test
  void logoutRevokesAccessAndRefreshTokensImmediately() throws Exception {
    fixtures.user("medico.g", DEFAULT_HOSPITAL, Role.DOCTOR);
    var login = fixtures.login(mockMvc, "medico.g", PASSWORD);
    String access = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    String refresh = refreshTokenOf(login);

    mockMvc
        .perform(
            post("/auth/logout")
                .with(bearerToken(access))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/auth/me").with(bearerToken(access))).andExpect(status().isUnauthorized());
    assertCode(refresh(refresh), 401, "INVALID_REFRESH_TOKEN");
  }

  @Test
  void deactivationTakesEffectOnExistingToken() throws Exception {
    var user = fixtures.user("medico.h", DEFAULT_HOSPITAL, Role.DOCTOR);
    fixtures.user("admin.h", DEFAULT_HOSPITAL, Role.ADMIN);
    var token = fixtures.accessToken(mockMvc, "medico.h");

    mockMvc
        .perform(
            patch("/users/" + user.getId() + "/deactivate")
                .with(fixtures.bearer(mockMvc, "admin.h")))
        .andExpect(status().isOk());

    mockMvc.perform(get("/auth/me").with(bearerToken(token))).andExpect(status().isUnauthorized());
  }

  // ---------- Troca de senha ----------

  @Test
  void temporaryPasswordBlocksEverythingExceptPasswordChange() throws Exception {
    fixtures.userWithTemporaryPassword("novo.a", DEFAULT_HOSPITAL, Role.ADMIN);
    var token = bearerToken(fixtures.accessToken(mockMvc, "novo.a"));

    mockMvc
        .perform(get("/users").with(token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    mockMvc
        .perform(get("/auth/me").with(token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));

    mockMvc
        .perform(changePassword(PASSWORD, "Nova-Senha-2026").with(token))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/users").with(token)).andExpect(status().isOk());
    assertThat(fixtures.login(mockMvc, "novo.a", "Nova-Senha-2026").getResponse().getStatus())
        .isEqualTo(200);
  }

  @Test
  void changePasswordValidatesCurrentAndNewPassword() throws Exception {
    fixtures.user("medico.i", DEFAULT_HOSPITAL, Role.DOCTOR);
    var token = fixtures.bearer(mockMvc, "medico.i");

    mockMvc
        .perform(changePassword("Errada-12345", "Nova-Senha-2026").with(token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    mockMvc
        .perform(changePassword(PASSWORD, "fraca").with(token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.newPassword").exists());
    mockMvc
        .perform(changePassword(PASSWORD, PASSWORD).with(token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PASSWORD_REUSED"));
  }

  @Test
  void changingPasswordEndsOtherSessionsButKeepsCurrent() throws Exception {
    fixtures.user("medico.j", DEFAULT_HOSPITAL, Role.DOCTOR);
    var current = fixtures.accessToken(mockMvc, "medico.j");
    var other = fixtures.accessToken(mockMvc, "medico.j");

    mockMvc
        .perform(changePassword(PASSWORD, "Nova-Senha-2026").with(bearerToken(current)))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/auth/me").with(bearerToken(current))).andExpect(status().isOk());
    mockMvc.perform(get("/auth/me").with(bearerToken(other))).andExpect(status().isUnauthorized());
  }

  // ---------- Auxiliares ----------

  private MvcResult refresh(String refreshToken) throws Exception {
    return mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andReturn();
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      changePassword(String current, String next) {
    return post("/auth/change-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"currentPassword":"%s","newPassword":"%s"}"""
                .formatted(current, next));
  }

  private static String refreshTokenOf(MvcResult result) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
  }

  private static void assertCode(MvcResult result, int status, String code) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.code"))
        .isEqualTo(code);
  }
}
