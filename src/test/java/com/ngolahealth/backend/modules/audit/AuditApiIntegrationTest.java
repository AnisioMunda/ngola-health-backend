package com.ngolahealth.backend.modules.audit;

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
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEvent;
import com.ngolahealth.backend.modules.audit.domain.AuditEventRepository;
import com.ngolahealth.backend.modules.audit.domain.AuditOutcome;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.support.AuthFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Prova que cada acção fica registada, com autor, alterações e âmbito correctos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuditApiIntegrationTest.UnauditedController.class)
@Transactional
class AuditApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private AuditEventRepository events;

  private RequestPostProcessor root;
  private RequestPostProcessor admin;

  /** Escrita que não regista evento explícito, para testar a rede de segurança. */
  @RestController
  static class UnauditedController {
    @PostMapping("/test-only/unaudited")
    void write() {}
  }

  @BeforeEach
  void setUp() throws Exception {
    fixtures.user("root", null, Role.SUPER_ADMIN);
    fixtures.user("admin.a", DEFAULT_HOSPITAL, Role.ADMIN);
    root = fixtures.bearer(mockMvc, "root");
    admin = fixtures.bearer(mockMvc, "admin.a");
  }

  @Test
  void hospitalCreationIsRecordedWithActorSnapshotAndRequestId() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/hospitals")
                    .with(root)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Clínica Girassol","code":"CGI-001","type":"CLINIC",
                         "province":"Luanda"}"""))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    var event = single(id, AuditAction.CREATE);
    assertThat(event.getActorUsername()).isEqualTo("root");
    assertThat(event.getActorId()).isNotNull();
    assertThat(event.getHospitalId()).hasToString(id);
    assertThat(event.getSummary()).contains("Clínica Girassol");
    assertThat(event.getChanges()).contains("\"code\"").contains("CGI-001");
    assertThat(event.getRequestId()).isEqualTo(result.getResponse().getHeader("X-Request-Id"));
    assertThat(event.getRequestPath()).endsWith("/hospitals");
    assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
  }

  @Test
  void userUpdateRecordsOnlyChangedFields() throws Exception {
    var user = fixtures.user("enf.rui", DEFAULT_HOSPITAL, Role.NURSE);

    mockMvc
        .perform(
            put("/users/" + user.getId())
                .with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Rui Novo Nome","email":"enf.rui@teste.ao","roles":["NURSE"]}"""))
        .andExpect(status().isOk());

    var event = single(user.getId().toString(), AuditAction.UPDATE);
    assertThat(event.getChanges())
        .contains("\"fullName\"")
        .contains("Rui Novo Nome")
        .doesNotContain("\"email\"")
        .doesNotContain("\"version\"");
    assertThat(event.getHospitalId()).isEqualTo(DEFAULT_HOSPITAL);
  }

  @Test
  void loginEventsAreRecordedEvenWhenLoginFails() throws Exception {
    var user = fixtures.user("med.log", DEFAULT_HOSPITAL, Role.DOCTOR);

    fixtures.login(mockMvc, "med.log", "Errada-12345");
    fixtures.login(mockMvc, "ninguem", "Errada-12345");
    fixtures.login(mockMvc, "med.log", PASSWORD);

    var failed = single(user.getId().toString(), AuditAction.LOGIN_FAILED);
    assertThat(failed.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
    assertThat(failed.getActorUsername()).isEqualTo("med.log");
    assertThat(single(user.getId().toString(), AuditAction.LOGIN).getHospitalId())
        .isEqualTo(DEFAULT_HOSPITAL);
    assertThat(events.findAll())
        .anyMatch(
            e ->
                e.getAction() == AuditAction.LOGIN_FAILED
                    && e.getEntityId() == null
                    && "ninguem".equals(e.getActorUsername()));
  }

  @Test
  void lockAndPasswordResetAreRecorded() throws Exception {
    var user = fixtures.user("med.lock", DEFAULT_HOSPITAL, Role.DOCTOR);
    for (int i = 0; i < 5; i++) {
      fixtures.login(mockMvc, "med.lock", "Errada-12345");
    }
    assertThat(single(user.getId().toString(), AuditAction.ACCOUNT_LOCKED).getOutcome())
        .isEqualTo(AuditOutcome.DENIED);

    mockMvc
        .perform(
            post("/users/" + user.getId() + "/reset-password")
                .with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"temporaryPassword\":\"Reposta-2026x\"}"))
        .andExpect(status().isOk());
    assertThat(single(user.getId().toString(), AuditAction.PASSWORD_RESET).getActorUsername())
        .isEqualTo("admin.a");
  }

  @Test
  void accessDeniedIsRecorded() throws Exception {
    fixtures.user("medico", DEFAULT_HOSPITAL, Role.DOCTOR);
    mockMvc
        .perform(get("/users").with(fixtures.bearer(mockMvc, "medico")))
        .andExpect(status().isForbidden());

    assertThat(events.findAll())
        .anyMatch(
            e ->
                e.getAction() == AuditAction.ACCESS_DENIED
                    && "medico".equals(e.getActorUsername())
                    && e.getOutcome() == AuditOutcome.DENIED
                    && DEFAULT_HOSPITAL.equals(e.getHospitalId()));
  }

  @Test
  void safetyNetRecordsWritesWithoutExplicitEvent() throws Exception {
    mockMvc.perform(post("/test-only/unaudited").with(admin)).andExpect(status().isOk());

    assertThat(events.findAll())
        .anyMatch(
            e ->
                e.getAction() == AuditAction.REQUEST
                    && e.getRequestPath().endsWith("/test-only/unaudited")
                    && "admin.a".equals(e.getActorUsername()));
  }

  @Test
  void noOpActivationDoesNotCreateGenericEvent() throws Exception {
    mockMvc
        .perform(patch("/hospitals/" + DEFAULT_HOSPITAL + "/activate").with(root))
        .andExpect(status().isOk());
    assertThat(events.findAll()).noneMatch(e -> e.getAction() == AuditAction.REQUEST);
  }

  @Test
  void adminSeesOnlyOwnHospitalAndManagerCanRead() throws Exception {
    var other = fixtures.hospital("HOT-001");
    fixtures.user("med.outro", other, Role.DOCTOR);
    fixtures.user("med.local", DEFAULT_HOSPITAL, Role.DOCTOR);
    fixtures.user("gestor", DEFAULT_HOSPITAL, Role.MANAGER);
    fixtures.login(mockMvc, "med.outro", PASSWORD);
    fixtures.login(mockMvc, "med.local", PASSWORD);

    mockMvc
        .perform(get("/audit").param("action", "LOGIN").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].actorUsername", hasItem("med.local")))
        .andExpect(jsonPath("$.content[*].actorUsername", not(hasItem("med.outro"))))
        .andExpect(jsonPath("$.content[*].actorUsername", not(hasItem("root"))));
    mockMvc
        .perform(get("/audit").param("action", "LOGIN").with(root))
        .andExpect(jsonPath("$.content[*].actorUsername", hasItem("med.outro")))
        .andExpect(jsonPath("$.content[*].actorUsername", hasItem("root")));
    mockMvc
        .perform(get("/audit").with(fixtures.bearer(mockMvc, "gestor")))
        .andExpect(status().isOk());

    var foreign =
        events.findAll().stream()
            .filter(e -> "med.outro".equals(e.getActorUsername()))
            .findFirst()
            .orElseThrow();
    mockMvc.perform(get("/audit/" + foreign.getId()).with(admin)).andExpect(status().isNotFound());
    mockMvc
        .perform(get("/audit/" + foreign.getId()).with(root))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actorUsername").value("med.outro"));
  }

  @Test
  void doctorCannotReadAudit() throws Exception {
    fixtures.user("medico.x", DEFAULT_HOSPITAL, Role.DOCTOR);
    mockMvc
        .perform(get("/audit").with(fixtures.bearer(mockMvc, "medico.x")))
        .andExpect(status().isForbidden());
  }

  @Test
  void filtersByEntityAndPeriodAndReturnsChangesAsJson() throws Exception {
    var user = fixtures.user("enf.ana", DEFAULT_HOSPITAL, Role.NURSE);
    mockMvc
        .perform(patch("/users/" + user.getId() + "/deactivate").with(admin))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/audit")
                .param("entityType", "USER")
                .param("entityId", user.getId().toString())
                .with(admin))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].action").value("DEACTIVATE"));

    mockMvc
        .perform(
            get("/audit")
                .param("from", "2000-01-01T00:00:00Z")
                .param("to", "2000-01-02T00:00:00Z")
                .with(admin))
        .andExpect(jsonPath("$.totalElements").value(0));
    mockMvc
        .perform(
            get("/audit")
                .param("from", "2000-01-02T00:00:00Z")
                .param("to", "2000-01-01T00:00:00Z")
                .with(admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
  }

  private AuditEvent single(String entityId, AuditAction action) {
    List<AuditEvent> matches =
        events.findAll().stream()
            .filter(e -> entityId.equals(e.getEntityId()) && e.getAction() == action)
            .toList();
    assertThat(matches).as("eventos %s de %s", action, entityId).hasSize(1);
    return matches.getFirst();
  }
}
