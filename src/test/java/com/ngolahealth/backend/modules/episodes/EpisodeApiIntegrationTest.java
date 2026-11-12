package com.ngolahealth.backend.modules.episodes;

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
import com.ngolahealth.backend.support.TenantScenario;
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

/** Ciclo de vida, permissões por acto clínico, isolamento e auditoria dos episódios. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EpisodeApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;

  private TenantScenario scenario;
  private UUID hospital;
  private UUID otherHospital;
  private UUID doctorId;
  private RequestPostProcessor reception;
  private RequestPostProcessor nurse;
  private RequestPostProcessor doctor;
  private String patientId;

  @BeforeEach
  void setUp() throws Exception {
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospital = scenario.hospital("EA");
    otherHospital = scenario.hospital("EB");
    reception = scenario.as("rec", hospital, Role.RECEPTIONIST);
    nurse = scenario.as("enf", hospital, Role.NURSE);
    String doctorUsername = scenario.user("med", hospital, Role.DOCTOR);
    doctorId = scenario.userId(doctorUsername);
    doctor = scenario.login(doctorUsername);
    patientId = createPatient(reception, "Ana Silva");
  }

  @AfterEach
  void cleanUp() {
    scenario.cleanUp();
  }

  @Test
  void fullLifecycleWithRolesPerClinicalAct() throws Exception {
    String id = createEpisode(reception, "OUTPATIENT", doctorId.toString());

    // Recepção não inicia nem regista vitais; enfermagem inicia e regista vitais.
    perform(patch("/episodes/" + id + "/start"), reception).andExpect(status().isForbidden());
    perform(patch("/episodes/" + id + "/start"), nurse)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.startedByName").exists());
    perform(
            json(
                put("/episodes/" + id + "/vitals"),
                "{\"systolicBp\":120,\"diastolicBp\":80,\"heartRate\":72,\"temperature\":36.8}"),
            nurse)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vitals.systolicBp").value(120))
        .andExpect(jsonPath("$.vitals.temperature").value(36.8));

    // Só o médico regista diagnóstico e conclui; concluir exige diagnóstico.
    perform(json(put("/episodes/" + id + "/clinical-notes"), "{\"diagnosis\":\"x\"}"), nurse)
        .andExpect(status().isForbidden());
    perform(patch("/episodes/" + id + "/complete"), doctor)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DIAGNOSIS_REQUIRED"));
    perform(
            json(
                put("/episodes/" + id + "/clinical-notes"),
                "{\"symptoms\":\"Febre há 3 dias\",\"diagnosis\":\"Malária não complicada\"}"),
            doctor)
        .andExpect(status().isOk());
    perform(patch("/episodes/" + id + "/complete"), doctor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.completedByName").exists());

    // Fechado = só de leitura.
    perform(json(put("/episodes/" + id + "/clinical-notes"), "{\"diagnosis\":\"outro\"}"), doctor)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EPISODE_CLOSED"));
    perform(json(patch("/episodes/" + id + "/cancel"), "{\"reason\":\"engano\"}"), doctor)
        .andExpect(status().isConflict());

    List<String> actions =
        jdbc.queryForList(
            "SELECT action FROM audit_events WHERE entity_type = 'EPISODE' AND entity_id = ?"
                + " ORDER BY occurred_at",
            String.class,
            id);
    assertThat(actions).containsExactly("CREATE", "START", "UPDATE", "UPDATE", "COMPLETE");
  }

  @Test
  void invalidTransitionsAreRejected() throws Exception {
    String id = createEpisode(reception, "EMERGENCY", null);
    perform(patch("/episodes/" + id + "/complete"), doctor)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_EPISODE_STATUS"));
    perform(json(put("/episodes/" + id + "/vitals"), "{\"heartRate\":80}"), nurse)
        .andExpect(status().isConflict());
  }

  @Test
  void examCanBeCompletedWithoutDiagnosis() throws Exception {
    String id = createEpisode(reception, "EXAM", null);
    perform(patch("/episodes/" + id + "/start"), nurse).andExpect(status().isOk());
    perform(patch("/episodes/" + id + "/complete"), doctor).andExpect(status().isOk());
  }

  @Test
  void cancellationRules() throws Exception {
    String scheduled = createEpisode(reception, "OUTPATIENT", null);
    perform(json(patch("/episodes/" + scheduled + "/cancel"), "{\"reason\":\" \"}"), reception)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.reason").exists());
    perform(
            json(patch("/episodes/" + scheduled + "/cancel"), "{\"reason\":\"Doente faltou\"}"),
            reception)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.cancellationReason").value("Doente faltou"));

    String started = createEpisode(reception, "OUTPATIENT", null);
    perform(patch("/episodes/" + started + "/start"), nurse).andExpect(status().isOk());
    perform(json(patch("/episodes/" + started + "/cancel"), "{\"reason\":\"x\"}"), reception)
        .andExpect(status().isForbidden());
    perform(json(patch("/episodes/" + started + "/cancel"), "{\"reason\":\"Erro\"}"), doctor)
        .andExpect(status().isOk());
  }

  @Test
  void doctorMustBeActiveDoctorOfSameHospital() throws Exception {
    String foreignDoctor = scenario.user("med.fora", otherHospital, Role.DOCTOR);
    perform(
            json(
                post("/episodes"),
                episodeBody(patientId, "OUTPATIENT", scenario.userId(foreignDoctor).toString())),
            reception)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DOCTOR"));
    String notDoctor = scenario.user("enf2", hospital, Role.NURSE);
    perform(
            json(
                post("/episodes"),
                episodeBody(patientId, "OUTPATIENT", scenario.userId(notDoctor).toString())),
            reception)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DOCTOR"));

    perform(get("/staff/doctors"), reception)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(doctorId.toString())))
        .andExpect(jsonPath("$[*].id", not(hasItem(scenario.userId(foreignDoctor).toString()))));
  }

  @Test
  void episodesAndPatientsAreIsolatedBetweenHospitals() throws Exception {
    String id = createEpisode(reception, "OUTPATIENT", null);
    var foreignReception = scenario.as("rec.fora", otherHospital, Role.RECEPTIONIST);

    perform(get("/episodes/" + id), foreignReception).andExpect(status().isNotFound());
    perform(get("/episodes"), foreignReception)
        .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));
    // Não se pode abrir episódio para paciente de outro hospital.
    perform(json(post("/episodes"), episodeBody(patientId, "OUTPATIENT", null)), foreignReception)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
  }

  @Test
  void worklistFiltersAndShowsPatientNames() throws Exception {
    String mine = createEpisode(reception, "OUTPATIENT", doctorId.toString());
    createEpisode(reception, "EMERGENCY", null);

    perform(
            get("/episodes")
                .param("doctorId", doctorId.toString())
                .param("status", "SCHEDULED", "IN_PROGRESS")
                .param("order", "asc"),
            doctor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(mine))
        .andExpect(jsonPath("$.content[0].patientName").value("Ana Silva"))
        .andExpect(jsonPath("$.content[0].patientRecordNumber").value("000001"));
    perform(get("/episodes").param("patientId", patientId), reception)
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void vitalsPlausibility() throws Exception {
    String id = createEpisode(reception, "OUTPATIENT", null);
    perform(patch("/episodes/" + id + "/start"), nurse);
    perform(
            json(
                put("/episodes/" + id + "/vitals"),
                "{\"temperature\":368,\"oxygenSaturation\":120}"),
            nurse)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.temperature").exists())
        .andExpect(jsonPath("$.errors.oxygenSaturation").exists());
    perform(
            json(put("/episodes/" + id + "/vitals"), "{\"systolicBp\":80,\"diastolicBp\":120}"),
            nurse)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_BLOOD_PRESSURE"));
  }

  // ---------- Auxiliares ----------

  private String createPatient(RequestPostProcessor as, String name) throws Exception {
    var result =
        perform(
                json(
                    post("/patients"),
                    "{\"fullName\":\""
                        + name
                        + "\",\"birthDate\":\"1990-05-01\",\"gender\":\"FEMALE\"}"),
                as)
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String createEpisode(RequestPostProcessor as, String type, String doctor)
      throws Exception {
    var result =
        perform(json(post("/episodes"), episodeBody(patientId, type, doctor)), as)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private static String episodeBody(String patient, String type, String doctor) {
    return """
        {"patientId":"%s","type":"%s","doctorId":%s,"reason":"Consulta de rotina"}"""
        .formatted(patient, type, doctor == null ? "null" : "\"" + doctor + "\"");
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
