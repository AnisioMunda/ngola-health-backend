package com.ngolahealth.backend.modules.inpatient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.support.AuthFixtures;
import com.ngolahealth.backend.support.TenantScenario;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.hamcrest.Matchers;
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
 * Enfermarias, camas, internamento com episódio, regras de admissão, transferência, alta, anulação
 * e concorrência.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InpatientApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;

  private TenantScenario scenario;
  private UUID hospital;
  private RequestPostProcessor admin;
  private RequestPostProcessor doctor;
  private RequestPostProcessor otherDoctor;
  private RequestPostProcessor nurse;
  private RequestPostProcessor reception;
  private String otherDoctorId;
  private String ana;
  private String rui;
  private String eva;
  private String femaleWard;
  private String f1;
  private String f2;
  private String m1;
  private String icu1;

  @BeforeEach
  void setUp() throws Exception {
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospital = scenario.hospital("IN");
    admin = scenario.as("adm", hospital, Role.ADMIN);
    doctor = scenario.as("med", hospital, Role.DOCTOR);
    otherDoctor = scenario.as("med2", hospital, Role.DOCTOR);
    nurse = scenario.as("enf", hospital, Role.NURSE);
    reception = scenario.as("rec", hospital, Role.RECEPTIONIST);
    otherDoctorId =
        jdbc.queryForObject(
            "SELECT id FROM users WHERE username LIKE 'med2.%' AND hospital_id = ?",
            String.class, hospital.toString());
    ana = patient("Ana Silva", "FEMALE");
    rui = patient("Rui Costa", "MALE");
    eva = patient("Eva Lopes", "FEMALE");
    femaleWard = ward("MF", "Medicina Mulheres", "FEMALE");
    f1 = bed(femaleWard, "1");
    f2 = bed(femaleWard, "2");
    m1 = bed(ward("MH", "Medicina Homens", "MALE"), "1");
    icu1 = bed(ward("UCI", "Cuidados Intensivos", "MIXED"), "1");
  }

  @AfterEach
  void cleanUp() {
    scenario.cleanUp();
  }

  @Test
  void admissionOpensAnInpatientEpisodeAndOccupiesTheBed() throws Exception {
    String body =
        admit(ana, f1, otherDoctorId)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.number").value(Matchers.startsWith("INT-")))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.wardName").value("Medicina Mulheres"))
            .andExpect(jsonPath("$.bedCode").value("1"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String episodeId = JsonPath.read(body, "$.episodeId");
    perform(get("/episodes/" + episodeId), doctor)
        .andExpect(jsonPath("$.type").value("INPATIENT"))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    // O episódio de internamento só fecha pela alta.
    perform(patch("/episodes/" + episodeId + "/complete"), doctor)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EPISODE_MANAGED_BY_ADMISSION"));
    perform(
            json(
                post("/episodes"),
                "{\"patientId\":\"" + rui + "\",\"type\":\"INPATIENT\",\"reason\":\"x\"}"),
            reception)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INPATIENT_VIA_ADMISSION"));

    perform(get("/inpatient/wards/" + femaleWard + "/map"), reception)
        .andExpect(jsonPath("$.ward.occupied").value(1))
        .andExpect(jsonPath("$.ward.available").value(1))
        .andExpect(jsonPath("$.beds[0].status").value("OCCUPIED"))
        .andExpect(jsonPath("$.beds[0].occupant.patientName").value("Ana Silva"))
        .andExpect(jsonPath("$.beds[1].occupant").doesNotExist());
    perform(get("/inpatient/admissions/episode/" + episodeId), nurse)
        .andExpect(jsonPath("$.patientName").value("Ana Silva"));
    // O médico responsável (que não foi quem internou) é avisado.
    perform(get("/notifications"), otherDoctor)
        .andExpect(jsonPath("$.content[0].type").value("INPATIENT_ASSIGNED"));
    perform(get("/notifications/unread-count"), doctor).andExpect(jsonPath("$.count").value(0));
  }

  @Test
  void admissionRules() throws Exception {
    admit(rui, f1, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WARD_GENDER_MISMATCH"));
    admit(ana, f1, null).andExpect(status().isCreated());
    admit(ana, f2, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PATIENT_ALREADY_ADMITTED"));
    admit(eva, f1, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BED_NOT_AVAILABLE"));
    perform(json(post("/inpatient/admissions"), admitBody(eva, f2, null)), nurse)
        .andExpect(status().isForbidden());
    perform(json(patch("/inpatient/beds/" + f2 + "/status"), "{\"status\":\"OCCUPIED\"}"), nurse)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("OCCUPIED_VIA_ADMISSION"));
    perform(json(patch("/inpatient/beds/" + f1 + "/status"), "{\"status\":\"MAINTENANCE\"}"), nurse)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BED_OCCUPIED"));
    perform(patch("/inpatient/wards/" + femaleWard + "/deactivate"), admin)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WARD_HAS_PATIENTS"));
    perform(
            json(
                patch("/inpatient/beds/" + f2 + "/status"),
                "{\"status\":\"MAINTENANCE\",\"note\":\"Colchão\"}"),
            nurse)
        .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    admit(eva, f2, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BED_NOT_AVAILABLE"));
    perform(json(post("/inpatient/wards"), wardBody("MF", "Outra", "MIXED")), admin)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WARD_CODE_IN_USE"));
  }

  @Test
  void transferThenDischargeClosesTheEpisodeAndFreesTheBedForCleaning() throws Exception {
    String body = admit(ana, f1, null).andReturn().getResponse().getContentAsString();
    String id = JsonPath.read(body, "$.id");
    String episodeId = JsonPath.read(body, "$.episodeId");

    perform(
            json(
                post("/inpatient/admissions/" + id + "/transfer"),
                "{\"toBedId\":\"" + m1 + "\",\"reason\":\"x\"}"),
            nurse)
        .andExpect(jsonPath("$.code").value("WARD_GENDER_MISMATCH"));
    perform(
            json(
                post("/inpatient/admissions/" + id + "/transfer"),
                "{\"toBedId\":\"" + icu1 + "\",\"reason\":\"Agravamento respiratório\"}"),
            nurse)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.wardName").value("Cuidados Intensivos"))
        .andExpect(jsonPath("$.transfers.length()").value(1))
        .andExpect(jsonPath("$.transfers[0].fromWardName").value("Medicina Mulheres"));
    assertThat(bedStatus(f1)).isEqualTo("CLEANING");
    assertThat(bedStatus(icu1)).isEqualTo("OCCUPIED");

    perform(json(post("/inpatient/admissions/" + id + "/discharge"), dischargeBody()), nurse)
        .andExpect(status().isForbidden());
    perform(
            json(
                post("/inpatient/admissions/" + id + "/discharge"), "{\"condition\":\"IMPROVED\"}"),
            doctor)
        .andExpect(status().isBadRequest());
    perform(json(post("/inpatient/admissions/" + id + "/discharge"), dischargeBody()), doctor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISCHARGED"))
        .andExpect(jsonPath("$.dischargeCondition").value("IMPROVED"));
    assertThat(bedStatus(icu1)).isEqualTo("CLEANING");
    perform(get("/episodes/" + episodeId), doctor)
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.diagnosis").value("Pneumonia adquirida na comunidade"));

    // Limpa a cama e o paciente pode voltar a ser internado.
    perform(json(patch("/inpatient/beds/" + icu1 + "/status"), "{\"status\":\"AVAILABLE\"}"), nurse)
        .andExpect(jsonPath("$.status").value("AVAILABLE"));
    admit(ana, icu1, null).andExpect(status().isCreated());
    List<String> actions =
        jdbc.queryForList(
            "SELECT action FROM audit_events WHERE entity_type = 'ADMISSION' AND entity_id = ?",
            String.class,
            id);
    assertThat(actions).contains("CREATE", "TRANSFER", "DISCHARGE");
  }

  @Test
  void cancellationOnlyWithin24Hours() throws Exception {
    String body = admit(ana, f1, null).andReturn().getResponse().getContentAsString();
    String id = JsonPath.read(body, "$.id");
    String episodeId = JsonPath.read(body, "$.episodeId");
    perform(
            json(
                patch("/inpatient/admissions/" + id + "/cancel"),
                "{\"reason\":\"Paciente errado\"}"),
            doctor)
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    perform(get("/episodes/" + episodeId), doctor)
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    assertThat(bedStatus(f1)).isEqualTo("CLEANING");

    String second =
        JsonPath.read(admit(eva, f2, null).andReturn().getResponse().getContentAsString(), "$.id");
    jdbc.update(
        "UPDATE admissions SET admitted_at = DATE_SUB(admitted_at, INTERVAL 25 HOUR) WHERE id = ?",
        second);
    perform(
            json(patch("/inpatient/admissions/" + second + "/cancel"), "{\"reason\":\"x\"}"),
            doctor)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANCEL_WINDOW_EXPIRED"));
  }

  @Test
  void concurrentAdmissionsNeverShareABedOrAdmitTwice() throws Exception {
    // Duas doentes para a mesma cama ao mesmo tempo.
    assertThat(race(List.of(admitBody(ana, f1, null), admitBody(eva, f1, null))))
        .containsExactlyInAnyOrder(201, 409);
    // O mesmo doente para duas camas ao mesmo tempo.
    assertThat(race(List.of(admitBody(rui, m1, null), admitBody(rui, icu1, null))))
        .containsExactlyInAnyOrder(201, 409);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM admissions WHERE hospital_id = ? AND status = 'ACTIVE'",
                Integer.class,
                hospital.toString()))
        .isEqualTo(2);
  }

  // ---------- Auxiliares ----------

  private List<Integer> race(List<String> bodies) throws Exception {
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(bodies.size());
    List<Callable<Integer>> tasks = new ArrayList<>();
    for (var body : bodies) {
      tasks.add(
          () -> {
            start.await();
            return perform(json(post("/inpatient/admissions"), body), doctor)
                .andReturn()
                .getResponse()
                .getStatus();
          });
    }
    var futures = tasks.stream().map(pool::submit).toList();
    start.countDown();
    List<Integer> statuses = new ArrayList<>();
    for (var f : futures) {
      statuses.add(f.get());
    }
    pool.shutdown();
    return statuses;
  }

  private ResultActions admit(String patient, String bed, String responsible) throws Exception {
    return perform(
        json(post("/inpatient/admissions"), admitBody(patient, bed, responsible)), doctor);
  }

  private static String admitBody(String patient, String bed, String responsible) {
    return """
        {"patientId":"%s","bedId":"%s","responsibleDoctorId":%s,"reason":"Pneumonia"}"""
        .formatted(patient, bed, responsible == null ? "null" : "\"" + responsible + "\"");
  }

  private static String dischargeBody() {
    return """
        {"condition":"IMPROVED","diagnosis":"Pneumonia adquirida na comunidade","summary":"Antibioterapia 7 dias"}""";
  }

  private String bedStatus(String bed) {
    return jdbc.queryForObject("SELECT status FROM beds WHERE id = ?", String.class, bed);
  }

  private String patient(String name, String gender) throws Exception {
    return id(
        perform(
            json(
                post("/patients"),
                "{\"fullName\":\"%s\",\"birthDate\":\"1980-01-01\",\"gender\":\"%s\"}"
                    .formatted(name, gender)),
            reception));
  }

  private static String wardBody(String code, String name, String gender) {
    return """
        {"code":"%s","name":"%s","type":"MEDICINE","genderPolicy":"%s","floor":"2"}"""
        .formatted(code, name, gender);
  }

  private String ward(String code, String name, String gender) throws Exception {
    return id(perform(json(post("/inpatient/wards"), wardBody(code, name, gender)), admin));
  }

  private String bed(String ward, String code) throws Exception {
    return id(
        perform(
            json(
                post("/inpatient/beds"),
                "{\"wardId\":\"%s\",\"code\":\"%s\",\"type\":\"STANDARD\"}".formatted(ward, code)),
            admin));
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
