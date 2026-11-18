package com.ngolahealth.backend.modules.reports;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.hamcrest.Matchers;
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

/**
 * Indicadores calculados sobre dados reais criados pela API: triagem (tempo-alvo), internamento
 * (dias-cama por enfermaria seguindo transferências), financeiro; isolamento entre hospitais e
 * permissões.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class ReportsApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;

  private TenantScenario scenario;
  private UUID hospital;
  private RequestPostProcessor admin;
  private RequestPostProcessor doctor;
  private RequestPostProcessor nurse;
  private RequestPostProcessor finance;
  private RequestPostProcessor reception;

  @BeforeEach
  void setUp() throws Exception {
    clock.reset();
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospital = scenario.hospital("RP");
    jdbc.update("UPDATE hospitals SET tax_id = '5417000001' WHERE id = ?", hospital.toString());
    admin = scenario.as("adm", hospital, Role.ADMIN);
    doctor = scenario.as("med", hospital, Role.DOCTOR);
    nurse = scenario.as("enf", hospital, Role.NURSE);
    finance = scenario.as("fin", hospital, Role.FINANCIAL);
    reception = scenario.as("rec", hospital, Role.RECEPTIONIST);
  }

  @AfterEach
  void cleanUp() {
    scenario.cleanUp();
    clock.reset();
  }

  @Test
  void triageWaitAgainstManchesterTargets() throws Exception {
    // AMARELO (alvo 60 min) chamado aos 5 min: dentro; LARANJA (alvo 10 min) chamado aos 20: fora.
    String yellow = id(perform(json(post("/triage"), triageBody("YELLOW")), nurse));
    String orange = id(perform(json(post("/triage"), triageBody("ORANGE")), nurse));
    clock.advance(Duration.ofMinutes(5));
    perform(patch("/triage/" + yellow + "/call"), nurse).andExpect(status().isOk());
    clock.advance(Duration.ofMinutes(15));
    perform(patch("/triage/" + orange + "/call"), nurse).andExpect(status().isOk());

    perform(get("/reports/dashboard"), admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.triage.total").value(2))
        .andExpect(jsonPath("$.triage.withinTargetPercent").value(50.0))
        .andExpect(jsonPath("$.triage.avgWaitMinutes").value(12.5))
        .andExpect(jsonPath("$.triage.avgWaitByPriority.ORANGE").value(20.0))
        .andExpect(jsonPath("$.daily.length()").value(30))
        .andExpect(jsonPath("$.daily[29].triage").value(2));
  }

  @Test
  void occupancyFollowsTransfersAndDashboardCountsInpatientFlow() throws Exception {
    String ana = patient("Ana Silva", "FEMALE");
    String wardA = ward("MED", "Medicina");
    String a1 = bed(wardA, "1");
    bed(wardA, "2");
    String wardB = ward("UCI", "Cuidados intensivos");
    String b1 = bed(wardB, "1");
    String admission =
        id(
            perform(
                json(
                    post("/inpatient/admissions"),
                    "{\"patientId\":\""
                        + ana
                        + "\",\"bedId\":\""
                        + a1
                        + "\",\"reason\":\"Pneumonia\"}"),
                doctor));
    perform(
            json(
                post("/inpatient/admissions/" + admission + "/transfer"),
                "{\"toBedId\":\"" + b1 + "\",\"reason\":\"Agravamento\"}"),
            doctor)
        .andExpect(status().isOk());
    // Recua no tempo: entrou há 3 dias, foi transferida há 1 dia, tem alta agora.
    jdbc.update(
        "UPDATE admissions SET admitted_at = DATE_SUB(admitted_at, INTERVAL 3 DAY) WHERE id = ?",
        admission);
    jdbc.update(
        "UPDATE admission_transfers SET transferred_at = DATE_SUB(transferred_at, INTERVAL 1 DAY) WHERE admission_id = ?",
        admission);
    perform(
            json(
                post("/inpatient/admissions/" + admission + "/discharge"),
                "{\"condition\":\"IMPROVED\",\"diagnosis\":\"Pneumonia\"}"),
            doctor)
        .andExpect(status().isOk());

    perform(get("/reports/occupancy"), admin)
        .andExpect(jsonPath("$.days").value(30))
        .andExpect(
            jsonPath("$.wards[?(@.wardName=='Medicina')].bedDays").value(Matchers.contains(2.0)))
        .andExpect(
            jsonPath("$.wards[?(@.wardName=='Medicina')].admissions").value(Matchers.contains(1)))
        .andExpect(
            jsonPath("$.wards[?(@.wardName=='Medicina')].occupancyPercent")
                .value(Matchers.contains(3.3)))
        .andExpect(
            jsonPath("$.wards[?(@.wardName=='Cuidados intensivos')].bedDays")
                .value(Matchers.contains(1.0)))
        .andExpect(
            jsonPath("$.wards[?(@.wardName=='Cuidados intensivos')].discharges")
                .value(Matchers.contains(1)))
        .andExpect(
            jsonPath("$.wards[?(@.wardName=='Cuidados intensivos')].avgLengthOfStayDays")
                .value(Matchers.contains(3.0)))
        .andExpect(jsonPath("$.total.bedDays").value(3.0));
    perform(get("/reports/dashboard"), admin)
        .andExpect(jsonPath("$.inpatient.activeBeds").value(3))
        .andExpect(jsonPath("$.inpatient.discharges").value(1))
        .andExpect(jsonPath("$.inpatient.avgLengthOfStayDays").value(3.0))
        .andExpect(jsonPath("$.patients.active").value(1));

    var csv =
        perform(get("/reports/occupancy.csv"), admin)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertThat(csv.getContentType()).startsWith("text/csv");
    var text = new String(csv.getContentAsByteArray(), StandardCharsets.UTF_8);
    assertThat(text).startsWith("﻿Enfermaria;Camas;").contains("Medicina;2;0;1;0;2,0;3,3;");
  }

  @Test
  void financialFiguresAndIsolationBetweenHospitals() throws Exception {
    String ana = patient("Ana Silva", "FEMALE");
    String ft = invoice(ana, "FT", 10000);
    perform(post("/financial/invoices/" + ft + "/issue"), reception).andExpect(status().isOk());
    perform(
        json(
            post("/financial/invoices/" + ft + "/payments"),
            "{\"amount\":4000,\"method\":\"CASH\"}"),
        reception);
    String fr = invoice(ana, "FR", 2500);
    perform(
        json(post("/financial/invoices/" + fr + "/issue"), "{\"paymentMethod\":\"CARD\"}"),
        reception);
    perform(
            json(post("/financial/invoices/" + fr + "/credit-note"), "{\"reason\":\"Erro\"}"),
            finance)
        .andExpect(status().isCreated());

    // Outro hospital com uma factura: não pode aparecer nos números deste.
    UUID other = scenario.hospital("RX");
    jdbc.update("UPDATE hospitals SET tax_id = '5417000002' WHERE id = ?", other.toString());
    var otherDesk = scenario.as("rec2", other, Role.RECEPTIONIST);
    String bob =
        id(
            perform(
                json(
                    post("/patients"),
                    "{\"fullName\":\"Bob\",\"birthDate\":\"1980-01-01\",\"gender\":\"MALE\"}"),
                otherDesk));
    String otherFr =
        id(perform(json(post("/financial/invoices"), invoiceBody(bob, "FR", 99999)), otherDesk));
    perform(
        json(post("/financial/invoices/" + otherFr + "/issue"), "{\"paymentMethod\":\"CASH\"}"),
        otherDesk);

    perform(get("/reports/financial"), finance)
        .andExpect(jsonPath("$.summary.invoiced").value(12500.0))
        .andExpect(jsonPath("$.summary.credited").value(2500.0))
        .andExpect(jsonPath("$.summary.net").value(10000.0))
        .andExpect(jsonPath("$.summary.received").value(6500.0))
        .andExpect(jsonPath("$.summary.outstanding").value(6000.0))
        .andExpect(jsonPath("$.byPaymentMethod[0].key").value("CASH"))
        .andExpect(jsonPath("$.topServices[0].key").value("Consulta"))
        .andExpect(jsonPath("$.daily[29].received").value(6500.0));
    perform(get("/reports/dashboard"), admin)
        .andExpect(jsonPath("$.finance.invoiced").value(12500.0));
    var csv =
        new String(
            perform(get("/reports/financial.csv"), finance)
                .andReturn()
                .getResponse()
                .getContentAsByteArray(),
            StandardCharsets.UTF_8);
    assertThat(csv).contains("Total;12500,00;6500,00");
  }

  @Test
  void permissionsAndPeriodValidation() throws Exception {
    perform(get("/reports/dashboard"), finance).andExpect(status().isForbidden());
    perform(get("/reports/dashboard"), nurse).andExpect(status().isForbidden());
    perform(get("/reports/financial"), nurse).andExpect(status().isForbidden());
    perform(get("/reports/dashboard").param("from", "2026-01-10").param("to", "2026-01-01"), admin)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    perform(get("/reports/dashboard").param("from", "2024-01-01").param("to", "2026-01-01"), admin)
        .andExpect(jsonPath("$.code").value("RANGE_TOO_LONG"));
    var platform = scenario.as("plat", null, Role.SUPER_ADMIN);
    perform(get("/reports/dashboard"), platform).andExpect(status().isForbidden());
  }

  // ---------- Auxiliares ----------

  private static String triageBody(String priority) {
    return """
        {"tempName":"Desconhecido","tempAge":40,"tempGender":"MALE","priority":"%s","chiefComplaint":"Dor torácica"}"""
        .formatted(priority);
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

  private String ward(String code, String name) throws Exception {
    return id(
        perform(
            json(
                post("/inpatient/wards"),
                "{\"code\":\"%s\",\"name\":\"%s\",\"type\":\"MEDICINE\",\"genderPolicy\":\"MIXED\"}"
                    .formatted(code, name)),
            admin));
  }

  private String bed(String ward, String code) throws Exception {
    return id(
        perform(
            json(
                post("/inpatient/beds"),
                "{\"wardId\":\"%s\",\"code\":\"%s\",\"type\":\"STANDARD\"}".formatted(ward, code)),
            admin));
  }

  private static String invoiceBody(String patient, String type, int amount) {
    return """
        {"documentType":"%s","patientId":"%s","lines":[{"description":"Consulta","quantity":1,"unitPrice":%d,"vatRate":0,"vatExemptionReason":"Isento"}]}"""
        .formatted(type, patient, amount);
  }

  private String invoice(String patient, String type, int amount) throws Exception {
    return id(
        perform(json(post("/financial/invoices"), invoiceBody(patient, type, amount)), reception));
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
