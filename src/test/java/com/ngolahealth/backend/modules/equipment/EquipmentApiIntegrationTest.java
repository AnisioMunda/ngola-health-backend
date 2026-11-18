package com.ngolahealth.backend.modules.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ngolahealth.backend.modules.equipment.application.EquipmentDueJob;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.support.AuthFixtures;
import com.ngolahealth.backend.support.TenantScenario;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** Inventário, avarias (estado e avisos), manutenções (datas e estado), abate e permissões. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EquipmentApiIntegrationTest {

  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Luanda"));

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EquipmentDueJob dueJob;

  private TenantScenario scenario;
  private UUID hospital;
  private RequestPostProcessor manager;
  private RequestPostProcessor nurse;

  @BeforeEach
  void setUp() throws Exception {
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospital = scenario.hospital("EQ");
    manager = scenario.as("ger", hospital, Role.MANAGER);
    nurse = scenario.as("enf", hospital, Role.NURSE);
  }

  @AfterEach
  void cleanUp() {
    scenario.cleanUp();
  }

  @Test
  void criticalFaultTakesEquipmentOutOfServiceUntilCorrectiveMaintenance() throws Exception {
    String ventilator =
        equipment(
            "VENT-01", "Ventilador", "LIFE_SUPPORT", "HIGH", "\"maintenanceIntervalDays\":180");
    perform(get("/equipment/" + ventilator), nurse)
        .andExpect(jsonPath("$.status").value("OPERATIONAL"))
        .andExpect(jsonPath("$.nextMaintenanceDate").value(TODAY.plusDays(180).toString()));

    // A enfermeira reporta; num equipamento crítico fica sempre fora de serviço.
    perform(
            json(
                post("/equipment/" + ventilator + "/faults"),
                "{\"description\":\"Alarme de pressão constante\",\"outOfService\":false}"),
            nurse)
        .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"))
        .andExpect(jsonPath("$.faults[0].outOfService").value(true));
    perform(get("/notifications"), manager)
        .andExpect(jsonPath("$.content[0].type").value("EQUIPMENT_FAULT"))
        .andExpect(jsonPath("$.content[0].priority").value("CRITICAL"));
    String fault =
        JsonPath.read(
            perform(get("/equipment/faults"), nurse).andReturn().getResponse().getContentAsString(),
            "$[0].id");

    perform(
            json(
                post("/equipment/" + ventilator + "/maintenance"),
                maintenance("CORRECTIVE", "PASSED", fault)),
            nurse)
        .andExpect(status().isForbidden());
    perform(
            json(
                post("/equipment/" + ventilator + "/maintenance"),
                maintenance("PREVENTIVE", "PASSED", fault)),
            manager)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CORRECTIVE_REQUIRED"));
    // Correctiva falhada: continua fora de serviço e a avaria continua aberta.
    perform(
            json(
                post("/equipment/" + ventilator + "/maintenance"),
                maintenance("CORRECTIVE", "FAILED", fault)),
            manager)
        .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"))
        .andExpect(jsonPath("$.faults[0].status").value("OPEN"));
    perform(
            json(
                post("/equipment/" + ventilator + "/maintenance"),
                maintenance("CORRECTIVE", "PASSED", fault)),
            manager)
        .andExpect(jsonPath("$.status").value("OPERATIONAL"))
        .andExpect(jsonPath("$.faults[0].status").value("RESOLVED"))
        .andExpect(jsonPath("$.maintenance.length()").value(2))
        .andExpect(jsonPath("$.maintenanceCostTotal").value(3000.0));
  }

  @Test
  void maintenanceUpdatesNextDatesAndNonBlockingFaultsKeepItInService() throws Exception {
    String monitor =
        equipment(
            "MON-07",
            "Monitor multiparamétrico",
            "MONITORING",
            "MEDIUM",
            "\"maintenanceIntervalDays\":90,\"calibrationIntervalDays\":365,\"nextCalibrationDate\":\""
                + TODAY.minusDays(1)
                + "\"");
    perform(get("/equipment/" + monitor), manager)
        .andExpect(jsonPath("$.calibrationOverdue").value(true));
    perform(get("/equipment/due").param("days", "0"), manager)
        .andExpect(jsonPath("$[0].code").value("MON-07"));
    assertThat(dueJob.run()).isGreaterThanOrEqualTo(1);
    perform(get("/notifications"), manager)
        .andExpect(
            jsonPath("$.content[?(@.type=='EQUIPMENT_DUE')].message")
                .value(
                    org.hamcrest.Matchers.contains(
                        org.hamcrest.Matchers.containsString("1 em atraso"))));

    perform(
            json(
                post("/equipment/" + monitor + "/maintenance"),
                maintenance("CALIBRATION", "PASSED", null)),
            manager)
        .andExpect(jsonPath("$.nextCalibrationDate").value(TODAY.plusDays(365).toString()))
        .andExpect(jsonPath("$.calibrationOverdue").value(false));

    // Avaria que não impede o uso: continua operacional; descartá-la fecha-a.
    perform(
            json(post("/equipment/" + monitor + "/faults"), "{\"description\":\"Ecrã riscado\"}"),
            nurse)
        .andExpect(jsonPath("$.status").value("OPERATIONAL"));
    String fault =
        JsonPath.read(
            perform(get("/equipment/faults"), nurse).andReturn().getResponse().getContentAsString(),
            "$[0].id");
    perform(
            json(patch("/equipment/faults/" + fault + "/dismiss"), "{\"reason\":\"Estético\"}"),
            manager)
        .andExpect(jsonPath("$.faults[0].status").value("DISMISSED"));
  }

  @Test
  void inventoryRulesAndRetirement() throws Exception {
    String bed =
        equipment("CAMA-ELEC-1", "Cama eléctrica", "MOBILITY", "LOW", "\"purchasePrice\":450000");
    perform(
            json(post("/equipment"), body("cama-elec-1", "Outra", "MOBILITY", "LOW", null)),
            manager)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EQUIPMENT_CODE_IN_USE"));
    perform(
            json(
                post("/equipment"),
                body(
                    "X1",
                    "X",
                    "OTHER",
                    "LOW",
                    "\"nextMaintenanceDate\":\"" + TODAY.plusDays(5) + "\"")),
            manager)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INTERVAL_REQUIRED"));
    perform(json(post("/equipment"), body("X2", "X", "OTHER", "LOW", null)), nurse)
        .andExpect(status().isForbidden());

    perform(patch("/equipment/" + bed + "/send-to-maintenance"), manager)
        .andExpect(jsonPath("$.status").value("UNDER_MAINTENANCE"));
    perform(json(patch("/equipment/" + bed + "/retire"), "{\"reason\":\"Irreparável\"}"), manager)
        .andExpect(jsonPath("$.status").value("RETIRED"))
        .andExpect(jsonPath("$.retirementReason").value("Irreparável"));
    perform(json(post("/equipment/" + bed + "/faults"), "{\"description\":\"x\"}"), nurse)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EQUIPMENT_RETIRED"));
    perform(get("/equipment"), nurse).andExpect(jsonPath("$.totalElements").value(0));
    perform(get("/equipment").param("includeRetired", "true"), nurse)
        .andExpect(jsonPath("$.totalElements").value(1));
    perform(get("/equipment/overview"), manager).andExpect(jsonPath("$.retired").value(1));
    List<String> actions =
        jdbc.queryForList(
            "SELECT action FROM audit_events WHERE entity_type = 'EQUIPMENT' AND entity_id = ?",
            String.class,
            bed);
    assertThat(actions).contains("CREATE", "RETIRE");
  }

  // ---------- Auxiliares ----------

  private String equipment(
      String code, String name, String category, String criticality, String extra)
      throws Exception {
    var r =
        perform(json(post("/equipment"), body(code, name, category, criticality, extra)), manager)
            .andReturn()
            .getResponse();
    assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
    return JsonPath.read(r.getContentAsString(), "$.id");
  }

  private static String body(
      String code, String name, String category, String criticality, String extra) {
    return """
        {"code":"%s","name":"%s","category":"%s","criticality":"%s"%s}"""
        .formatted(code, name, category, criticality, extra == null ? "" : "," + extra);
  }

  private static String maintenance(String type, String result, String fault) {
    return """
        {"type":"%s","performedOn":"%s","performedBy":"TecnoMed Lda","description":"Revisão","cost":1500,"result":"%s","faultId":%s}"""
        .formatted(type, TODAY, result, fault == null ? "null" : "\"" + fault + "\"");
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
