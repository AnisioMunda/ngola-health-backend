package com.ngolahealth.backend.modules.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.support.AuthFixtures;
import com.ngolahealth.backend.support.TenantScenario;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Activação por código entregue em mão, audiência própria dos tokens do portal, isolamento entre
 * doentes, só resultados validados e desactivação imediata.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;

  private TenantScenario scenario;
  private UUID hospital;
  private RequestPostProcessor reception;
  private RequestPostProcessor admin;
  private String ana;
  private String rui;

  @BeforeEach
  void setUp() throws Exception {
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospital = scenario.hospital("PT");
    reception = scenario.as("rec", hospital, Role.RECEPTIONIST);
    admin = scenario.as("adm", hospital, Role.ADMIN);
    ana = patient("Ana Silva", "1990-05-01");
    rui = patient("Rui Costa", "1985-02-10");
  }

  @AfterEach
  void cleanUp() {
    scenario.cleanUp();
  }

  @Test
  void activationWithCodeHandedAtReception() throws Exception {
    perform(get("/patients/" + ana + "/portal"), reception)
        .andExpect(jsonPath("$.hasAccount").value(false));
    String code = issueCode(ana);
    assertThat(code).matches("[A-Z2-9]{5}-[A-Z2-9]{5}");
    perform(get("/patients/" + ana + "/portal"), reception)
        .andExpect(jsonPath("$.pendingCodeExpiresAt").exists());
    // O código fica só como hash.
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM portal_activation_codes WHERE code_hash LIKE ?",
                Integer.class,
                "%" + code + "%"))
        .isZero();

    mockMvc
        .perform(json(post("/portal/activate"), activation(code, "1990-05-02", "ana.portal")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ACTIVATION"));
    assertThat(
            jdbc.queryForObject(
                "SELECT failed_attempts FROM portal_activation_codes WHERE patient_id = ?",
                Integer.class,
                ana))
        .isEqualTo(1);
    // Código escrito em minúsculas e sem hífen também serve.
    mockMvc
        .perform(
            json(
                post("/portal/activate"),
                activation(code.toLowerCase().replace("-", ""), "1990-05-01", "ana.portal")))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(json(post("/portal/activate"), activation(code, "1990-05-01", "ana.outra")))
        .andExpect(status().isBadRequest());

    perform(get("/patients/" + ana + "/portal"), reception)
        .andExpect(jsonPath("$.hasAccount").value(true))
        .andExpect(jsonPath("$.username").value("ana.portal"));
    perform(post("/patients/" + ana + "/portal/activation-code"), reception)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PORTAL_ACCOUNT_EXISTS"));
    // A conta do doente não aparece na lista de utilizadores do pessoal.
    perform(get("/users").param("search", "ana.portal"), admin)
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void codeStopsWorkingAfterFiveWrongBirthDates() throws Exception {
    String code = issueCode(ana);
    for (int i = 0; i < 5; i++) {
      mockMvc.perform(json(post("/portal/activate"), activation(code, "2000-01-01", "ana.portal")));
    }
    mockMvc
        .perform(json(post("/portal/activate"), activation(code, "1990-05-01", "ana.portal")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void portalTokensAndStaffTokensDoNotCross() throws Exception {
    var patient = activateAndLogin(ana, "1990-05-01", "ana.portal");
    var token = fixtures.accessToken(mockMvc, "ana.portal");
    var payload =
        new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    assertThat(payload).contains("\"aud\":\"ngolahealth-portal\"");

    perform(get("/portal/me"), patient)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Ana Silva"))
        .andExpect(jsonPath("$.hospitalName").exists());
    perform(get("/patients"), patient).andExpect(status().isForbidden());
    perform(get("/patients/" + rui), patient).andExpect(status().isForbidden());
    perform(get("/auth/me"), patient).andExpect(status().isOk());
    perform(get("/notifications/unread-count"), patient).andExpect(status().isOk());
    perform(get("/portal/me"), reception).andExpect(status().isForbidden());
  }

  @Test
  void patientSeesOnlyOwnDataAndOnlyValidatedResults() throws Exception {
    var nurse = scenario.as("enf", hospital, Role.NURSE);
    var doctor = scenario.as("med", hospital, Role.DOCTOR);
    var lab = scenario.as("lab", hospital, Role.LAB_TECHNICIAN);
    // Ana: pedido com dois exames, só um validado.
    String episode =
        id(
            perform(
                json(
                    post("/episodes"),
                    "{\"patientId\":\"" + ana + "\",\"type\":\"OUTPATIENT\",\"reason\":\"Febre\"}"),
                reception));
    perform(patch("/episodes/" + episode + "/start"), nurse).andExpect(status().isOk());
    String hb =
        labTest(
            lab,
            "HB",
            "Hemoglobina",
            "NUMERIC",
            "\"unit\":\"g/dL\",\"referenceLow\":12,\"referenceHigh\":16");
    String ge = labTest(lab, "GE", "Gota espessa", "QUALITATIVE", "\"referenceText\":\"Negativo\"");
    String request =
        id(
            perform(
                json(
                    post("/lab/requests"),
                    "{\"episodeId\":\"%s\",\"priority\":\"ROUTINE\",\"testIds\":[\"%s\",\"%s\"]}"
                        .formatted(episode, hb, ge)),
                doctor));
    var body =
        perform(get("/lab/requests/" + request), lab)
            .andReturn()
            .getResponse()
            .getContentAsString();
    String hbItem = JsonPath.<List<String>>read(body, "$.items[?(@.testCode=='HB')].id").getFirst();
    String geItem = JsonPath.<List<String>>read(body, "$.items[?(@.testCode=='GE')].id").getFirst();
    perform(json(post("/lab/requests/" + request + "/collect"), "{}"), lab);
    perform(post("/lab/requests/" + request + "/start"), lab);
    perform(
        json(
            put("/lab/requests/" + request + "/items/" + hbItem + "/result"),
            "{\"numericValue\":10.5}"),
        lab);
    perform(post("/lab/requests/" + request + "/items/" + hbItem + "/validate"), lab);
    perform(
        json(
            put("/lab/requests/" + request + "/items/" + geItem + "/result"),
            "{\"textValue\":\"Positivo\",\"abnormal\":true}"),
        lab);

    var patient = activateAndLogin(ana, "1990-05-01", "ana.portal");
    perform(get("/portal/lab-results"), patient)
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].results.length()").value(1))
        .andExpect(jsonPath("$[0].results[0].testName").value("Hemoglobina"))
        .andExpect(jsonPath("$[0].results[0].value").value("10.5"))
        .andExpect(jsonPath("$[0].results[0].flag").value("LOW"))
        .andExpect(jsonPath("$[0].results[0].reference").value("12–16"))
        .andExpect(jsonPath("$[0].pending").value(1));
    perform(get("/portal/visits"), patient)
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));
  }

  @Test
  void appointmentsListAndCancelOnlyOwn() throws Exception {
    var admin2 = admin;
    var doctor = scenario.as("med", hospital, Role.DOCTOR);
    String doctorId =
        jdbc.queryForObject(
            "SELECT id FROM users WHERE username LIKE 'med.%' AND hospital_id = ?",
            String.class, hospital.toString());
    var tomorrow = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Luanda")).plusDays(1);
    perform(
            json(
                post("/scheduling/schedules"),
                "{\"doctorId\":\"%s\",\"dayOfWeek\":\"%s\",\"startTime\":\"08:00\",\"endTime\":\"12:00\",\"slotMinutes\":30,\"capacity\":1}"
                    .formatted(doctorId, tomorrow.getDayOfWeek())),
            admin2)
        .andExpect(status().isCreated());
    String own =
        id(
            perform(
                json(post("/scheduling/appointments"), booking(ana, doctorId, tomorrow, "08:00")),
                reception));
    String other =
        id(
            perform(
                json(post("/scheduling/appointments"), booking(rui, doctorId, tomorrow, "09:00")),
                reception));

    var patient = activateAndLogin(ana, "1990-05-01", "ana.portal");
    perform(get("/portal/appointments"), patient)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(own))
        .andExpect(jsonPath("$[0].cancellable").value(true));
    perform(json(patch("/portal/appointments/" + other + "/cancel"), "{\"reason\":\"x\"}"), patient)
        .andExpect(status().isNotFound());
    perform(
            json(patch("/portal/appointments/" + own + "/cancel"), "{\"reason\":\"Viagem\"}"),
            patient)
        .andExpect(status().isNoContent());
    perform(get("/portal/appointments"), patient)
        .andExpect(jsonPath("$[0].status").value("CANCELLED"))
        .andExpect(jsonPath("$[0].cancellable").value(false));
    perform(get("/notifications"), doctor)
        .andExpect(jsonPath("$.content[0].type").value("APPOINTMENT_CANCELLED"));
  }

  private static String booking(
      String patient, String doctor, java.time.LocalDate date, String time) {
    return """
        {"patientId":"%s","doctorId":"%s","type":"CONSULTATION","date":"%s","startTime":"%s","reason":"Controlo"}"""
        .formatted(patient, doctor, date, time);
  }

  @Test
  void deactivationEndsTheSessionImmediately() throws Exception {
    var patient = activateAndLogin(ana, "1990-05-01", "ana.portal");
    perform(get("/portal/me"), patient).andExpect(status().isOk());
    perform(patch("/patients/" + ana + "/portal/deactivate"), reception)
        .andExpect(status().isForbidden());
    perform(patch("/patients/" + ana + "/portal/deactivate"), admin)
        .andExpect(jsonPath("$.accountActive").value(false));
    perform(get("/portal/me"), patient).andExpect(status().isUnauthorized());
  }

  // ---------- Auxiliares ----------

  private RequestPostProcessor activateAndLogin(String patientId, String birthDate, String username)
      throws Exception {
    String code = issueCode(patientId);
    mockMvc
        .perform(json(post("/portal/activate"), activation(code, birthDate, username)))
        .andExpect(status().isNoContent());
    return scenario.login(username);
  }

  private String issueCode(String patientId) throws Exception {
    var r =
        perform(post("/patients/" + patientId + "/portal/activation-code"), reception)
            .andReturn()
            .getResponse();
    assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
    return JsonPath.read(r.getContentAsString(), "$.code");
  }

  private static String activation(String code, String birthDate, String username) {
    return """
        {"code":"%s","birthDate":"%s","username":"%s","email":"%s@exemplo.ao","password":"%s"}"""
        .formatted(code, birthDate, username, username, AuthFixtures.PASSWORD);
  }

  private String patient(String name, String birthDate) throws Exception {
    return id(
        perform(
            json(
                post("/patients"),
                "{\"fullName\":\"%s\",\"birthDate\":\"%s\",\"gender\":\"FEMALE\"}"
                    .formatted(name, birthDate)),
            reception));
  }

  private String labTest(
      RequestPostProcessor lab, String code, String name, String type, String extra)
      throws Exception {
    return id(
        perform(
            json(
                post("/lab/tests"),
                """
                {"code":"%s","name":"%s","category":"HEMATOLOGY","sampleType":"BLOOD","resultType":"%s",%s,"turnaroundHours":24}"""
                    .formatted(code, name, type, extra)),
            lab));
  }

  private static String id(ResultActions result) throws Exception {
    var r = result.andReturn().getResponse();
    assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
    return JsonPath.read(r.getContentAsString(), "$.id");
  }

  private ResultActions perform(MockHttpServletRequestBuilder request, RequestPostProcessor as)
      throws Exception {
    return mockMvc.perform(request.with(as));
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder builder, String content) {
    return builder.contentType(MediaType.APPLICATION_JSON).content(content);
  }
}
