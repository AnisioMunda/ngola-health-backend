package com.ngolahealth.backend.modules.pharmacy;

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
import com.ngolahealth.backend.support.MutableClock;
import com.ngolahealth.backend.support.TenantScenario;
import com.ngolahealth.backend.support.TestClockConfig;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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

/** Catálogo, lotes, FEFO, livro de movimentos, alertas, permissões e concorrência. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class PharmacyApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;

  private TenantScenario scenario;
  private UUID hospital;
  private RequestPostProcessor pharmacist;
  private String pharmacistUsername;
  private RequestPostProcessor doctor;
  private LocalDate today;

  @BeforeEach
  void setUp() throws Exception {
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospital = scenario.hospital("FA");
    pharmacistUsername = scenario.user("farm", hospital, Role.PHARMACIST);
    pharmacist = scenario.login(pharmacistUsername);
    doctor = scenario.as("med", hospital, Role.DOCTOR);
    today = LocalDate.now(java.time.ZoneId.of("Africa/Luanda"));
  }

  @AfterEach
  void cleanUp() {
    clock.reset();
    scenario.cleanUp();
  }

  @Test
  void catalogRejectsDuplicatesAndDoctorsCannotEditIt() throws Exception {
    medication("Paracetamol", "500 mg", false);
    perform(
            json(post("/pharmacy/medications"), medicationBody(" paracetamol ", "500 mg", false)),
            pharmacist)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MEDICATION_DUPLICATE"));
    medication("Paracetamol", "1 g", false);

    perform(
            json(post("/pharmacy/medications"), medicationBody("Ibuprofeno", "400 mg", false)),
            doctor)
        .andExpect(status().isForbidden());
    perform(get("/pharmacy/medications").param("search", "parac"), doctor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void dispensingUsesFefoAndKeepsLedger() throws Exception {
    String med = medication("Paracetamol", "500 mg", false);
    receive(med, "LATE", today.plusDays(300), 10);
    receive(med, "EARLY", today.plusDays(30), 5);

    perform(
            json(post("/pharmacy/dispense"), "{\"medicationId\":\"" + med + "\",\"quantity\":7}"),
            pharmacist)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].batchNumber").value("EARLY"))
        .andExpect(jsonPath("$.lines[0].quantity").value(5))
        .andExpect(jsonPath("$.lines[1].batchNumber").value("LATE"))
        .andExpect(jsonPath("$.lines[1].quantity").value(2))
        .andExpect(jsonPath("$.remaining").value(8));

    perform(get("/pharmacy/medications/" + med), pharmacist)
        .andExpect(jsonPath("$.stockAvailable").value(8))
        .andExpect(jsonPath("$.nextExpiry").value(today.plusDays(300).toString()));
    perform(get("/pharmacy/medications/" + med + "/movements"), pharmacist)
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content[*].type", hasItem("DISPENSE")))
        .andExpect(jsonPath("$.content[*].type", hasItem("RECEIPT")));

    perform(
            json(post("/pharmacy/dispense"), "{\"medicationId\":\"" + med + "\",\"quantity\":9}"),
            pharmacist)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
  }

  @Test
  void prescriptionOnlyMedicationCannotBeDispensedDirectly() throws Exception {
    String med = medication("Amoxicilina", "500 mg", true);
    receive(med, "A1", today.plusDays(200), 20);
    perform(
            json(post("/pharmacy/dispense"), "{\"medicationId\":\"" + med + "\",\"quantity\":1}"),
            pharmacist)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRESCRIPTION_REQUIRED"));
  }

  @Test
  void expiredBatchesAreNotUsedAndCanBeWrittenOff() throws Exception {
    String med = medication("Paracetamol", "500 mg", false);
    String batch = receive(med, "OLD", today.plusDays(2), 5);
    receive(med, "NEW", today.plusDays(100), 3);

    perform(post("/pharmacy/batches/" + batch + "/write-off-expired"), pharmacist)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BATCH_NOT_EXPIRED"));

    clock.advance(Duration.ofDays(5));
    // A sessão (12 h) expirou com o avanço do relógio: novo login.
    pharmacist = scenario.login(pharmacistUsername);
    perform(get("/pharmacy/medications/" + med), pharmacist)
        .andExpect(jsonPath("$.stockAvailable").value(3));
    perform(get("/pharmacy/alerts").param("days", "30"), pharmacist)
        .andExpect(jsonPath("$.expired[0].batchNumber").value("OLD"));
    perform(
            json(post("/pharmacy/dispense"), "{\"medicationId\":\"" + med + "\",\"quantity\":4}"),
            pharmacist)
        .andExpect(status().isConflict());

    perform(post("/pharmacy/batches/" + batch + "/write-off-expired"), pharmacist)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantityAvailable").value(0));
  }

  @Test
  void adjustmentsRequireReasonAndNeverGoNegative() throws Exception {
    String med = medication("Paracetamol", "500 mg", false);
    String batch = receive(med, "B1", today.plusDays(100), 5);

    perform(
            json(
                post("/pharmacy/batches/" + batch + "/adjust"),
                "{\"kind\":\"COUNT_SHORTAGE\",\"quantity\":2}"),
            pharmacist)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.reason").exists());
    perform(
            json(
                post("/pharmacy/batches/" + batch + "/adjust"),
                "{\"kind\":\"COUNT_SHORTAGE\",\"quantity\":9,\"reason\":\"Contagem\"}"),
            pharmacist)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_BATCH_STOCK"));
    perform(
            json(
                post("/pharmacy/batches/" + batch + "/adjust"),
                "{\"kind\":\"DAMAGED\",\"quantity\":2,\"reason\":\"Embalagem partida\"}"),
            pharmacist)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantityAvailable").value(3));

    List<String> actions =
        jdbc.queryForList("SELECT action FROM audit_events WHERE entity_id = ?", String.class, med);
    assertThat(actions).contains("STOCK_RECEIPT", "STOCK_WRITE_OFF");
  }

  @Test
  void lowStockAlertAndDeactivation() throws Exception {
    String med = medication("Paracetamol", "500 mg", false);
    receive(med, "B1", today.plusDays(100), 5);
    perform(get("/pharmacy/alerts"), pharmacist)
        .andExpect(jsonPath("$.lowStock[*].id", hasItem(med)));

    perform(patch("/pharmacy/medications/" + med + "/deactivate"), pharmacist)
        .andExpect(status().isOk());
    perform(
            json(post("/pharmacy/dispense"), "{\"medicationId\":\"" + med + "\",\"quantity\":1}"),
            pharmacist)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MEDICATION_INACTIVE"));
    perform(get("/pharmacy/alerts"), pharmacist)
        .andExpect(jsonPath("$.lowStock[*].id", not(hasItem(med))));
  }

  @Test
  void updateWithOptimisticLocking() throws Exception {
    String med = medication("Paracetamol", "500 mg", false);
    var body =
        medicationBody("Paracetamol", "500 mg", false)
            .replace("\"version\":null", "\"version\":0")
            .replace("\"minStockLevel\":10", "\"minStockLevel\":50");
    perform(json(put("/pharmacy/medications/" + med), body), pharmacist)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.minStockLevel").value(50));
    perform(json(put("/pharmacy/medications/" + med), body), pharmacist)
        .andExpect(status().isConflict());
  }

  @Test
  void isolatedBetweenHospitals() throws Exception {
    String med = medication("Paracetamol", "500 mg", false);
    var other = scenario.as("farm.fora", scenario.hospital("FB"), Role.PHARMACIST);
    perform(get("/pharmacy/medications/" + med), other).andExpect(status().isNotFound());
    perform(
            json(post("/pharmacy/stock/receive"), receiveBody(med, "X", today.plusDays(50), 5)),
            other)
        .andExpect(status().isNotFound());
  }

  @Test
  void concurrentDispensesNeverOversell() throws Exception {
    String med = medication("Paracetamol", "500 mg", false);
    receive(med, "B1", today.plusDays(100), 10);

    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    List<Callable<Integer>> tasks = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      tasks.add(
          () -> {
            start.await();
            return mockMvc
                .perform(
                    json(
                            post("/pharmacy/dispense"),
                            "{\"medicationId\":\"" + med + "\",\"quantity\":6}")
                        .with(pharmacist))
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

    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    Integer left =
        jdbc.queryForObject(
            "SELECT quantity_available FROM stock_batches WHERE medication_id = ?",
            Integer.class,
            med);
    assertThat(left).isEqualTo(4);
  }

  // ---------- Auxiliares ----------

  private String medication(String name, String strength, boolean prescription) throws Exception {
    var result =
        perform(
                json(post("/pharmacy/medications"), medicationBody(name, strength, prescription)),
                pharmacist)
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String receive(String med, String batch, LocalDate expiry, int qty) throws Exception {
    var result =
        perform(
                json(post("/pharmacy/stock/receive"), receiveBody(med, batch, expiry, qty)),
                pharmacist)
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private static String medicationBody(String name, String strength, boolean prescription) {
    return """
        {"name":"%s","genericName":null,"dosageForm":"TABLET","strength":"%s",
         "dispensingUnit":"comprimido","requiresPrescription":%s,"minStockLevel":10,"version":null}"""
        .formatted(name, strength, prescription);
  }

  private static String receiveBody(String med, String batch, LocalDate expiry, int qty) {
    return """
        {"medicationId":"%s","batchNumber":"%s","expiryDate":"%s","quantity":%d,
         "unitCost":150.50,"supplier":"Fornecedor X"}"""
        .formatted(med, batch, expiry, qty);
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
