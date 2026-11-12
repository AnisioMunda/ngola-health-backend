package com.ngolahealth.backend.modules.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.support.AuthFixtures;
import com.ngolahealth.backend.support.MutableClock;
import com.ngolahealth.backend.support.TenantScenario;
import com.ngolahealth.backend.support.TestClockConfig;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Fila de Manchester, re-triagem, identificação, encaminhamento e isolamento. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class TriageApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;

  private TenantScenario scenario;
  private UUID hospital;
  private RequestPostProcessor nurse;
  private RequestPostProcessor reception;
  private String patientId;

  @BeforeEach
  void setUp() throws Exception {
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospital = scenario.hospital("TA");
    nurse = scenario.as("enf", hospital, Role.NURSE);
    reception = scenario.as("rec", hospital, Role.RECEPTIONIST);
    var result =
        perform(
                json(
                    post("/patients"),
                    "{\"fullName\":\"Ana Silva\",\"birthDate\":\"1990-05-01\",\"gender\":\"FEMALE\"}"),
                reception)
            .andExpect(status().isCreated())
            .andReturn();
    patientId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @AfterEach
  void cleanUp() {
    clock.reset();
    scenario.cleanUp();
  }

  @Test
  void queueIsOrderedByPriorityThenArrivalWithDailyTicketNumbers() throws Exception {
    String green = triage(patientBody("GREEN"));
    clock.advance(Duration.ofMinutes(1));
    String red = triage(tempBody("RED", "Desconhecido"));
    clock.advance(Duration.ofMinutes(1));
    String green2 = triage(tempBody("GREEN", "Desconhecida 2"));

    perform(get("/triage/queue"), nurse)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(red))
        .andExpect(jsonPath("$[1].id").value(green))
        .andExpect(jsonPath("$[2].id").value(green2))
        .andExpect(jsonPath("$[1].queueNumber").value(1))
        .andExpect(jsonPath("$[0].queueNumber").value(2))
        .andExpect(jsonPath("$[1].displayName").value("Ana Silva"))
        .andExpect(jsonPath("$[0].identified").value(false));
  }

  @Test
  void priorityIsRequiredAndIdentityIsRequired() throws Exception {
    perform(
            json(
                post("/triage"),
                "{\"patientId\":\"" + patientId + "\",\"chiefComplaint\":\"Dor\"}"),
            nurse)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.priority").value("Seleccione a prioridade."));
    perform(json(post("/triage"), "{\"priority\":\"RED\",\"chiefComplaint\":\"Dor\"}"), nurse)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PATIENT_IDENTITY_REQUIRED"));
    perform(json(post("/triage"), patientBody("RED")), reception).andExpect(status().isForbidden());
  }

  @Test
  void overdueFlagFollowsConfiguredTargets() throws Exception {
    String orange = triage(patientBody("ORANGE"));
    perform(get("/triage/" + orange), nurse)
        .andExpect(jsonPath("$.overdue").value(false))
        .andExpect(jsonPath("$.targetMinutes").value(10));
    clock.advance(Duration.ofMinutes(11));
    perform(get("/triage/" + orange), nurse)
        .andExpect(jsonPath("$.overdue").value(true))
        .andExpect(jsonPath("$.waitingMinutes").value(11));
    perform(get("/triage/stats"), nurse)
        .andExpect(jsonPath("$.overdue").value(1))
        .andExpect(jsonPath("$.waitingByPriority.ORANGE").value(1));
  }

  @Test
  void reTriageKeepsHistoryAndOnlyWhileWaiting() throws Exception {
    String id = triage(patientBody("GREEN"));
    perform(json(patch("/triage/" + id + "/priority"), "{\"priority\":\"ORANGE\"}"), nurse)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.reason").exists());
    perform(
            json(
                patch("/triage/" + id + "/priority"),
                "{\"priority\":\"ORANGE\",\"reason\":\"Agravamento da dor\"}"),
            nurse)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.priority").value("ORANGE"))
        .andExpect(jsonPath("$.priorityChanges[0].from").value("GREEN"))
        .andExpect(jsonPath("$.priorityChanges[0].reason").value("Agravamento da dor"));

    perform(patch("/triage/" + id + "/call"), nurse).andExpect(status().isOk());
    perform(
            json(patch("/triage/" + id + "/priority"), "{\"priority\":\"RED\",\"reason\":\"x\"}"),
            nurse)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_TRIAGE_STATUS"));
  }

  @Test
  void unidentifiedPatientMustBeIdentifiedBeforeEpisode() throws Exception {
    String id = triage(tempBody("YELLOW", "Desconhecido"));
    perform(patch("/triage/" + id + "/call"), nurse).andExpect(status().isOk());
    perform(post("/triage/" + id + "/episode"), nurse)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_IDENTIFIED"));

    perform(
            json(patch("/triage/" + id + "/patient"), "{\"patientId\":\"" + patientId + "\"}"),
            nurse)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.identified").value(true))
        .andExpect(jsonPath("$.displayName").value("Ana Silva"));

    var result =
        perform(post("/triage/" + id + "/episode"), nurse)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.episodeId").exists())
            .andReturn();
    String episodeId = JsonPath.read(result.getResponse().getContentAsString(), "$.episodeId");
    perform(get("/episodes/" + episodeId), nurse)
        .andExpect(jsonPath("$.type").value("EMERGENCY"))
        .andExpect(jsonPath("$.patientId").value(patientId))
        .andExpect(jsonPath("$.reason").value("Urgência (triagem YELLOW): Dor abdominal"));
  }

  @Test
  void leftOnlyFromWaitingAndReceptionCanMarkIt() throws Exception {
    String id = triage(patientBody("BLUE"));
    perform(patch("/triage/" + id + "/left"), reception)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LEFT"));
    perform(patch("/triage/" + id + "/call"), nurse)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRIAGE_CLOSED"));
    perform(get("/triage/queue"), nurse).andExpect(jsonPath("$[*].id", not(hasItem(id))));
  }

  @Test
  void isolatedBetweenHospitalsAndAudited() throws Exception {
    String id = triage(patientBody("RED"));
    var other = scenario.as("enf.fora", scenario.hospital("TB"), Role.NURSE);
    perform(get("/triage/" + id), other).andExpect(status().isNotFound());
    perform(get("/triage/queue"), other).andExpect(jsonPath("$[*].id", not(hasItem(id))));

    clock.advance(Duration.ofSeconds(1));
    perform(patch("/triage/" + id + "/call"), nurse).andExpect(status().isOk());
    clock.advance(Duration.ofSeconds(1));
    perform(patch("/triage/" + id + "/complete"), nurse).andExpect(status().isOk());
    List<String> actions =
        jdbc.queryForList(
            "SELECT action FROM audit_events WHERE entity_type = 'TRIAGE' AND entity_id = ?"
                + " ORDER BY occurred_at",
            String.class,
            id);
    assertThat(actions).containsExactly("CREATE", "START", "COMPLETE");
  }

  @Test
  void vitalsAreValidated() throws Exception {
    perform(
            json(
                post("/triage"),
                "{\"patientId\":\""
                    + patientId
                    + "\",\"priority\":\"RED\",\"chiefComplaint\":\"Dor\","
                    + "\"painScale\":11,\"vitals\":{\"oxygenSaturation\":120}}"),
            nurse)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.painScale").exists())
        .andExpect(jsonPath("$.errors['vitals.oxygenSaturation']").exists());
  }

  // ---------- Auxiliares ----------

  private String triage(String body) throws Exception {
    var result =
        perform(json(post("/triage"), body), nurse)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("WAITING"))
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String patientBody(String priority) {
    return """
        {"patientId":"%s","priority":"%s","chiefComplaint":"Dor abdominal","painScale":6,
         "vitals":{"systolicBp":130,"diastolicBp":85,"heartRate":96}}"""
        .formatted(patientId, priority);
  }

  private static String tempBody(String priority, String name) {
    return """
        {"tempName":"%s","tempAge":40,"tempGender":"MALE","priority":"%s",
         "chiefComplaint":"Dor abdominal"}"""
        .formatted(name, priority);
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
