package com.ngolahealth.backend.modules.users;

import static com.ngolahealth.backend.support.AuthFixtures.DEFAULT_HOSPITAL;
import static com.ngolahealth.backend.support.AuthFixtures.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.support.AuthFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de utilizadores: perfis, isolamento entre hospitais e regras de negócio. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;

  private UUID otherHospital;
  private RequestPostProcessor root;
  private RequestPostProcessor admin;

  @BeforeEach
  void setUp() throws Exception {
    otherHospital = fixtures.hospital("HOB-001");
    fixtures.user("root", null, Role.SUPER_ADMIN);
    fixtures.user("admin.a", DEFAULT_HOSPITAL, Role.ADMIN);
    root = fixtures.bearer(mockMvc, "root");
    admin = fixtures.bearer(mockMvc, "admin.a");
  }

  @Test
  void adminCreatesUserInOwnHospitalEvenIfAnotherIsRequested() throws Exception {
    var result =
        mockMvc
            .perform(json(post("/users"), create("enf.ana", "NURSE", otherHospital)).with(admin))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("enf.ana"))
            .andExpect(jsonPath("$.hospitalId").value(DEFAULT_HOSPITAL.toString()))
            .andExpect(jsonPath("$.hospitalName").value("Hospital Central de Luanda"))
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain("password");

    // A senha temporária funciona e obriga a troca.
    var login = fixtures.login(mockMvc, "enf.ana", "Temporaria-2026").getResponse();
    assertThat(login.getStatus()).isEqualTo(200);
    assertThat(login.getContentAsString()).contains("\"mustChangePassword\":true");
  }

  @Test
  void superAdminMustChooseHospital() throws Exception {
    mockMvc
        .perform(json(post("/users"), create("med.x", "DOCTOR", null)).with(root))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("HOSPITAL_REQUIRED"));
    mockMvc
        .perform(json(post("/users"), create("med.x", "DOCTOR", otherHospital)).with(root))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.hospitalId").value(otherHospital.toString()));
  }

  @Test
  void onlySuperAdminCreatesSuperAdminWithoutHospitalOrOtherRoles() throws Exception {
    mockMvc
        .perform(json(post("/users"), create("root.2", "SUPER_ADMIN", null)).with(admin))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(json(post("/users"), create("root.2", "SUPER_ADMIN\",\"ADMIN", null)).with(root))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ROLE"));
    mockMvc
        .perform(json(post("/users"), create("root.2", "SUPER_ADMIN", otherHospital)).with(root))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.hospitalId").doesNotExist());
  }

  @Test
  void duplicateUsernameOrEmailIsConflict() throws Exception {
    mockMvc
        .perform(json(post("/users"), create("Admin.A", "NURSE", null)).with(admin))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_IN_USE"));
    mockMvc
        .perform(
            json(
                    post("/users"),
                    create("outro", "NURSE", null).replace("outro@teste.ao", "ADMIN.A@teste.ao"))
                .with(admin))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
  }

  @Test
  void invalidPayloadReturnsFieldErrors() throws Exception {
    mockMvc
        .perform(
            json(
                    post("/users"),
                    """
                    {"fullName":"A","username":"a b","email":"x","roles":[],
                     "temporaryPassword":"fraca"}""")
                .with(admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.fullName").exists())
        .andExpect(jsonPath("$.errors.username").exists())
        .andExpect(jsonPath("$.errors.email").exists())
        .andExpect(jsonPath("$.errors.roles").value("Seleccione pelo menos um perfil."))
        .andExpect(jsonPath("$.errors.temporaryPassword").exists());
  }

  @Test
  void hospitalIsolation() throws Exception {
    var foreign = fixtures.user("med.outro", otherHospital, Role.DOCTOR);
    fixtures.user("med.local", DEFAULT_HOSPITAL, Role.DOCTOR);

    mockMvc
        .perform(get("/users").param("size", "100").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].username", hasItem("med.local")))
        .andExpect(jsonPath("$.content[*].username", not(hasItem("med.outro"))))
        .andExpect(jsonPath("$.content[*].username", not(hasItem("root"))));
    mockMvc
        .perform(get("/users/" + foreign.getId()).with(admin))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    mockMvc
        .perform(patch("/users/" + foreign.getId() + "/deactivate").with(admin))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/users").param("hospitalId", otherHospital.toString()).with(root))
        .andExpect(jsonPath("$.content[*].username", hasItem("med.outro")))
        .andExpect(jsonPath("$.content[*].username", not(hasItem("med.local"))));
  }

  @Test
  void searchFiltersByTextRoleAndStatus() throws Exception {
    fixtures.user("farm.joao", DEFAULT_HOSPITAL, Role.PHARMACIST);
    fixtures.user("farm.maria", DEFAULT_HOSPITAL, Role.PHARMACIST, Role.MANAGER);

    mockMvc
        .perform(get("/users").param("role", "PHARMACIST").with(admin))
        .andExpect(jsonPath("$.totalElements").value(2));
    mockMvc
        .perform(get("/users").param("search", "MARIA").with(admin))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].roles.length()").value(2));
    mockMvc
        .perform(get("/users").param("status", "INACTIVE").with(admin))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void rolePermissionsMatrix() throws Exception {
    fixtures.user("gestor", DEFAULT_HOSPITAL, Role.MANAGER);
    fixtures.user("medico", DEFAULT_HOSPITAL, Role.DOCTOR);
    var manager = fixtures.bearer(mockMvc, "gestor");
    var doctor = fixtures.bearer(mockMvc, "medico");

    mockMvc.perform(get("/users").with(manager)).andExpect(status().isOk());
    mockMvc
        .perform(json(post("/users"), create("x.y", "NURSE", null)).with(manager))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/users").with(doctor)).andExpect(status().isForbidden());
    mockMvc.perform(get("/hospitals/management").with(admin)).andExpect(status().isForbidden());
    mockMvc.perform(get("/hospitals/management").with(root)).andExpect(status().isOk());
  }

  @Test
  void adminCannotLockThemselvesOut() throws Exception {
    var self = fixtures.user("admin.b", DEFAULT_HOSPITAL, Role.ADMIN);
    var token = fixtures.bearer(mockMvc, "admin.b");

    mockMvc
        .perform(patch("/users/" + self.getId() + "/deactivate").with(token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
    mockMvc
        .perform(json(put("/users/" + self.getId()), update("DOCTOR", null)).with(token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_OWN_ADMIN_ROLE"));
  }

  @Test
  void updateChangesProfileAndRolesWithOptimisticLocking() throws Exception {
    var user = fixtures.user("enf.rui", DEFAULT_HOSPITAL, Role.NURSE);

    mockMvc
        .perform(json(put("/users/" + user.getId()), update("NURSE\",\"MANAGER", 0L)).with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Nome Actualizado"))
        .andExpect(jsonPath("$.roles.length()").value(2))
        .andExpect(jsonPath("$.version").value(1));
    mockMvc
        .perform(json(put("/users/" + user.getId()), update("NURSE", 0L)).with(admin))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
  }

  @Test
  void resetPasswordForcesChangeUnlocksAndEndsSessions() throws Exception {
    var user = fixtures.user("med.reset", DEFAULT_HOSPITAL, Role.DOCTOR);
    var oldToken = fixtures.bearer(mockMvc, "med.reset");
    for (int i = 0; i < 5; i++) {
      fixtures.login(mockMvc, "med.reset", "Errada-12345");
    }

    mockMvc
        .perform(
            json(
                    post("/users/" + user.getId() + "/reset-password"),
                    "{\"temporaryPassword\":\"Reposta-2026x\"}")
                .with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true))
        .andExpect(jsonPath("$.lockedUntil").doesNotExist());

    mockMvc.perform(get("/auth/me").with(oldToken)).andExpect(status().isUnauthorized());
    assertThat(fixtures.login(mockMvc, "med.reset", "Reposta-2026x").getResponse().getStatus())
        .isEqualTo(200);
    assertThat(fixtures.login(mockMvc, "med.reset", PASSWORD).getResponse().getStatus())
        .isEqualTo(401);
  }

  @Test
  void createReturnsLocationOfNewUser() throws Exception {
    var result =
        mockMvc
            .perform(json(post("/users"), create("rec.lia", "RECEPTIONIST", null)).with(admin))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    assertThat(result.getResponse().getHeader("Location")).endsWith("/users/" + id);
  }

  // ---------- Auxiliares ----------

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder builder, String content) {
    return builder.contentType(MediaType.APPLICATION_JSON).content(content);
  }

  /** {@code roles} é inserido entre aspas: use {@code A","B} para vários papéis. */
  private static String create(String username, String roles, UUID hospitalId) {
    return """
        {"fullName":"Utilizador %1$s","username":"%1$s","email":"%2$s@teste.ao",
         "roles":["%3$s"],"hospitalId":%4$s,"temporaryPassword":"Temporaria-2026"}"""
        .formatted(
            username,
            username.toLowerCase(),
            roles,
            hospitalId == null ? "null" : "\"" + hospitalId + "\"");
  }

  private static String update(String roles, Long version) {
    return """
        {"fullName":"Nome Actualizado","email":"actualizado-%s@teste.ao","roles":["%s"],
         "version":%s}"""
        .formatted(UUID.randomUUID(), roles, version);
  }
}
