package com.ngolahealth.backend.modules.laboratory;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Pedido no episódio, colheita, análise, resultado, validação, correcção, visibilidade e
 * permissões.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;

  private TenantScenario scenario;
  private RequestPostProcessor doctor;
  private RequestPostProcessor nurse;
  private RequestPostProcessor lab;
  private String episodeId;
  private String hemoglobin;
  private String malaria;

  @BeforeEach
  void setUp() throws Exception {
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    UUID hospital = scenario.hospital("LA");
    var reception = scenario.as("rec", hospital, Role.RECEPTIONIST);
    nurse = scenario.as("enf", hospital, Role.NURSE);
    doctor = scenario.as("med", hospital, Role.DOCTOR);
    lab = scenario.as("lab", hospital, Role.LAB_TECHNICIAN);
    String patientId =
        id(
            perform(
                    json(
                        post("/patients"),
                        "{\"fullName\":\"Ana Silva\",\"birthDate\":\"1990-05-01\",\"gender\":\"FEMALE\"}"),
                    reception)
                .andExpect(status().isCreated())
                .andReturn());
    episodeId =
        id(
            perform(
                    json(
                        post("/episodes"),
                        "{\"patientId\":\""
                            + patientId
                            + "\",\"type\":\"OUTPATIENT\",\"reason\":\"Febre\"}"),
                    reception)
                .andExpect(status().isCreated())
                .andReturn());
    perform(patch("/episodes/" + episodeId + "/start"), nurse).andExpect(status().isOk());
    hemoglobin =
        test(
            "HB",
            "Hemoglobina",
            "NUMERIC",
            "\"unit\":\"g/dL\",\"referenceLow\":12,\"referenceHigh\":16");
    malaria = test("GE", "Gota espessa (malária)", "QUALITATIVE", "\"referenceText\":\"Negativo\"");
  }

  @AfterEach
  void cleanUp() {
    scenario.cleanUp();
  }

  @Test
  void fullWorkflowWithValidationAndVisibility() throws Exception {
    String id = request("URGENT");
    var items = itemIds(id, lab);
    String hb = items.get(0);
    String ge = items.get(1);

    perform(post("/lab/requests/" + id + "/start"), lab)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_LAB_STATUS"));
    perform(json(post("/lab/requests/" + id + "/collect"), "{\"notes\":\"Punção venosa\"}"), nurse)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COLLECTED"))
        .andExpect(jsonPath("$.collectedByName").exists());
    perform(post("/lab/requests/" + id + "/start"), lab).andExpect(status().isOk());

    perform(
            json(
                put("/lab/requests/" + id + "/items/" + hb + "/result"), "{\"numericValue\":10.5}"),
            lab)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.id=='" + hb + "')].flag").value("LOW"));
    // Antes de validar, o médico vê o estado mas não o valor.
    perform(get("/lab/requests/" + id), doctor)
        .andExpect(jsonPath("$.items[?(@.id=='" + hb + "')].status").value("RESULTED"))
        .andExpect(jsonPath("$.items[?(@.id=='" + hb + "')].resultVisible").value(false))
        .andExpect(
            jsonPath("$.items[?(@.id=='" + hb + "')].numericValue")
                .value(org.hamcrest.Matchers.contains((Object) null)));

    perform(post("/lab/requests/" + id + "/items/" + hb + "/validate"), lab)
        .andExpect(jsonPath("$.status").value("IN_ANALYSIS"));
    perform(
            json(
                put("/lab/requests/" + id + "/items/" + ge + "/result"),
                "{\"textValue\":\"Positivo (P. falciparum)\",\"abnormal\":true}"),
            lab)
        .andExpect(status().isOk());
    perform(post("/lab/requests/" + id + "/items/" + ge + "/validate"), lab)
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    perform(get("/lab/requests/" + id), doctor)
        .andExpect(jsonPath("$.hasAbnormal").value(true))
        .andExpect(jsonPath("$.items[?(@.id=='" + hb + "')].numericValue").value(10.5))
        .andExpect(jsonPath("$.items[?(@.id=='" + ge + "')].flag").value("ABNORMAL"));
  }

  @Test
  void validatedResultCanOnlyChangeThroughAmendmentWithHistory() throws Exception {
    String id = request("ROUTINE");
    String hb = itemIds(id, lab).get(0);
    perform(json(post("/lab/requests/" + id + "/collect"), "{}"), lab);
    perform(post("/lab/requests/" + id + "/start"), lab);
    perform(
        json(put("/lab/requests/" + id + "/items/" + hb + "/result"), "{\"numericValue\":13}"),
        lab);
    perform(post("/lab/requests/" + id + "/items/" + hb + "/validate"), lab);

    perform(
            json(put("/lab/requests/" + id + "/items/" + hb + "/result"), "{\"numericValue\":8}"),
            lab)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RESULT_VALIDATED"));
    perform(
            json(post("/lab/requests/" + id + "/items/" + hb + "/amend"), "{\"numericValue\":8}"),
            lab)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.reason").exists());
    perform(
            json(
                post("/lab/requests/" + id + "/items/" + hb + "/amend"),
                "{\"numericValue\":8,\"reason\":\"Erro de transcrição\"}"),
            lab)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.id=='" + hb + "')].amended").value(true))
        .andExpect(jsonPath("$.items[?(@.id=='" + hb + "')].flag").value("LOW"))
        .andExpect(
            jsonPath("$.items[?(@.id=='" + hb + "')].amendments[0].previousNumeric").value(13.0))
        .andExpect(
            jsonPath("$.items[?(@.id=='" + hb + "')].amendments[0].reason")
                .value("Erro de transcrição"));
  }

  @Test
  void catalogChangesDoNotAlterExistingRequests() throws Exception {
    String id = request("ROUTINE");
    perform(
            json(
                put("/lab/tests/" + hemoglobin),
                "{\"code\":\"HB\",\"name\":\"Hemoglobina\",\"category\":\"HEMATOLOGY\",\"sampleType\":\"BLOOD\","
                    + "\"resultType\":\"NUMERIC\",\"unit\":\"g/dL\",\"referenceLow\":11,\"referenceHigh\":15,\"turnaroundHours\":24}"),
            lab)
        .andExpect(status().isOk());
    perform(get("/lab/requests/" + id), lab)
        .andExpect(jsonPath("$.items[?(@.testCode=='HB')].referenceLow").value(12.0));
  }

  @Test
  void permissionsPerAct() throws Exception {
    perform(json(post("/lab/requests"), requestBody("ROUTINE")), nurse)
        .andExpect(status().isForbidden());
    String id = request("ROUTINE");
    String hb = itemIds(id, lab).get(0);
    perform(json(post("/lab/requests/" + id + "/collect"), "{}"), doctor)
        .andExpect(status().isForbidden());
    perform(json(post("/lab/requests/" + id + "/collect"), "{}"), nurse).andExpect(status().isOk());
    perform(post("/lab/requests/" + id + "/start"), nurse).andExpect(status().isForbidden());
    perform(post("/lab/requests/" + id + "/start"), lab).andExpect(status().isOk());
    perform(
            json(put("/lab/requests/" + id + "/items/" + hb + "/result"), "{\"numericValue\":13}"),
            doctor)
        .andExpect(status().isForbidden());
    perform(json(patch("/lab/requests/" + id + "/cancel"), "{\"reason\":\"x\"}"), lab)
        .andExpect(status().isForbidden());
    perform(
            json(
                post("/lab/tests"),
                "{\"code\":\"X1\",\"name\":\"X\",\"category\":\"OTHER\",\"sampleType\":\"BLOOD\","
                    + "\"resultType\":\"QUALITATIVE\",\"turnaroundHours\":24}"),
            doctor)
        .andExpect(status().isForbidden());
  }

  @Test
  void cancelRequiresReasonAndBlocksWorkflow() throws Exception {
    String id = request("ROUTINE");
    perform(json(patch("/lab/requests/" + id + "/cancel"), "{\"reason\":\" \"}"), doctor)
        .andExpect(status().isBadRequest());
    perform(
            json(patch("/lab/requests/" + id + "/cancel"), "{\"reason\":\"Pedido em duplicado\"}"),
            doctor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.items[0].status").value("CANCELLED"));
    perform(json(post("/lab/requests/" + id + "/collect"), "{}"), nurse)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LAB_REQUEST_CANCELLED"));
  }

  @Test
  void worklistShowsUrgentFirstAndRequestsAreAudited() throws Exception {
    String routine = request("ROUTINE");
    String urgent = request("URGENT");
    perform(get("/lab/requests").param("open", "true"), lab)
        .andExpect(jsonPath("$.content[0].id").value(urgent))
        .andExpect(jsonPath("$.content[1].id").value(routine))
        .andExpect(jsonPath("$.content[0].number").value(org.hamcrest.Matchers.startsWith("LAB-")));
    perform(get("/lab/requests/episode/" + episodeId), doctor)
        .andExpect(jsonPath("$.length()").value(2));
    List<String> actions =
        jdbc.queryForList(
            "SELECT action FROM audit_events WHERE entity_type = 'LAB_REQUEST' AND entity_id = ?",
            String.class,
            urgent);
    assertThat(actions).contains("CREATE");
  }

  @Test
  void requestRules() throws Exception {
    perform(
            json(
                post("/lab/requests"),
                "{\"episodeId\":\""
                    + episodeId
                    + "\",\"priority\":\"ROUTINE\",\"testIds\":[\""
                    + hemoglobin
                    + "\",\""
                    + hemoglobin
                    + "\"]}"),
            doctor)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DUPLICATE_TEST"));
    perform(patch("/lab/tests/" + malaria + "/deactivate"), lab).andExpect(status().isOk());
    perform(json(post("/lab/requests"), requestBody("ROUTINE")), doctor)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LAB_TEST_INACTIVE"));
  }

  // ---------- Auxiliares ----------

  private String test(String code, String name, String type, String extra) throws Exception {
    return id(
        perform(
                json(
                    post("/lab/tests"),
                    """
        {"code":"%s","name":"%s","category":"HEMATOLOGY","sampleType":"BLOOD","resultType":"%s",%s,"turnaroundHours":24}"""
                        .formatted(code, name, type, extra)),
                lab)
            .andExpect(status().isCreated())
            .andReturn());
  }

  private String requestBody(String priority) {
    return """
        {"episodeId":"%s","priority":"%s","clinicalInfo":"Febre há 3 dias","testIds":["%s","%s"]}"""
        .formatted(episodeId, priority, hemoglobin, malaria);
  }

  private String request(String priority) throws Exception {
    return id(
        perform(json(post("/lab/requests"), requestBody(priority)), doctor)
            .andExpect(status().isCreated())
            .andReturn());
  }

  /**
   * Ids dos itens pela ordem de criação dos exames (Hemoglobina, Gota espessa: ordem alfabética).
   */
  private List<String> itemIds(String requestId, RequestPostProcessor as) throws Exception {
    var body =
        perform(get("/lab/requests/" + requestId), as)
            .andReturn()
            .getResponse()
            .getContentAsString();
    String hb = JsonPath.<List<String>>read(body, "$.items[?(@.testCode=='HB')].id").getFirst();
    String ge = JsonPath.<List<String>>read(body, "$.items[?(@.testCode=='GE')].id").getFirst();
    return List.of(hb, ge);
  }

  private static String id(MvcResult result) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
