package com.ngolahealth.backend.modules.prescriptions;

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
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Emissão no episódio, dispensa parcial/total por FEFO, caducidade, cancelamento e permissões. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class PrescriptionApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;

  private TenantScenario scenario;
  private UUID hospital;
  private RequestPostProcessor doctor;
  private RequestPostProcessor nurse;
  private RequestPostProcessor pharmacist;
  private String doctorUsername;
  private String pharmacistUsername;
  private String episodeId;
  private String patientId;
  private String amoxicillin;
  private String paracetamol;

  @BeforeEach
  void setUp() throws Exception {
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospital = scenario.hospital("RA");
    var reception = scenario.as("rec", hospital, Role.RECEPTIONIST);
    nurse = scenario.as("enf", hospital, Role.NURSE);
    doctorUsername = scenario.user("med", hospital, Role.DOCTOR);
    doctor = scenario.login(doctorUsername);
    pharmacistUsername = scenario.user("farm", hospital, Role.PHARMACIST);
    pharmacist = scenario.login(pharmacistUsername);

    patientId =
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

    amoxicillin = medication("Amoxicilina", "500 mg", true);
    paracetamol = medication("Paracetamol", "500 mg", false);
    var today = LocalDate.now(ZoneId.of("Africa/Luanda"));
    receive(amoxicillin, "AM-LATE", today.plusDays(300), 30);
    receive(amoxicillin, "AM-EARLY", today.plusDays(60), 10);
  }

  @AfterEach
  void cleanUp() {
    clock.reset();
    scenario.cleanUp();
  }

  @Test
  void doctorPrescribesInProgressEpisodeAndGetsNumberAndStockWarnings() throws Exception {
    var result =
        perform(json(post("/prescriptions"), prescription(21, 5)), doctor)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.number").value(org.hamcrest.Matchers.startsWith("RX-")))
            .andExpect(jsonPath("$.number").value(org.hamcrest.Matchers.endsWith("-000001")))
            .andExpect(jsonPath("$.patientName").value("Ana Silva"))
            .andExpect(jsonPath("$.effectiveStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.items[0].stockAvailable").value(40))
            .andExpect(jsonPath("$.items[1].stockAvailable").value(0))
            .andReturn();
    assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.validUntil"))
        .isEqualTo(LocalDate.now(ZoneId.of("Africa/Luanda")).plusDays(30).toString());

    perform(get("/prescriptions/episode/" + episodeId), nurse)
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void onlyDoctorsPrescribeAndOnlyInProgressEpisodes() throws Exception {
    perform(json(post("/prescriptions"), prescription(1, 1)), nurse)
        .andExpect(status().isForbidden());
    perform(json(post("/prescriptions"), prescription(1, 1)), pharmacist)
        .andExpect(status().isForbidden());

    perform(
            json(
                put_("/episodes/" + episodeId + "/clinical-notes"),
                "{\"diagnosis\":\"Amigdalite\"}"),
            doctor)
        .andExpect(status().isOk());
    perform(patch("/episodes/" + episodeId + "/complete"), doctor).andExpect(status().isOk());
    perform(json(post("/prescriptions"), prescription(1, 1)), doctor)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EPISODE_NOT_IN_PROGRESS"));
  }

  @Test
  void rejectsDuplicateMedicationAndEmptyPrescription() throws Exception {
    var dup =
        """
        {"episodeId":"%s","items":[%s,%s]}"""
            .formatted(episodeId, item(amoxicillin, 1), item(amoxicillin, 2));
    perform(json(post("/prescriptions"), dup), doctor)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DUPLICATE_MEDICATION"));
    perform(
            json(post("/prescriptions"), "{\"episodeId\":\"" + episodeId + "\",\"items\":[]}"),
            doctor)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.items").exists());
  }

  @Test
  void partialThenFullDispenseUsesFefoAndLinksLedger() throws Exception {
    String id = create(prescription(21, 5));
    var items = items(id);
    String amoxItem = items.get(0);

    perform(json(post("/prescriptions/" + id + "/dispense"), dispense(amoxItem, 12)), pharmacist)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PARTIALLY_DISPENSED"))
        .andExpect(jsonPath("$.items[0].remaining").value(9));

    List<String> batches =
        jdbc.queryForList(
            "SELECT b.batch_number FROM stock_movements m JOIN stock_batches b ON b.id = m.batch_id"
                + " WHERE m.reference_id = ? ORDER BY b.expiry_date",
            String.class,
            amoxItem);
    assertThat(batches).containsExactly("AM-EARLY", "AM-LATE");

    perform(json(post("/prescriptions/" + id + "/dispense"), dispense(amoxItem, 10)), pharmacist)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("QUANTITY_EXCEEDS_PRESCRIBED"));

    // Paracetamol sem stock: tudo ou nada — a amoxicilina também não sai.
    String body =
        """
        {"lines":[{"itemId":"%s","quantity":9},{"itemId":"%s","quantity":5}]}"""
            .formatted(amoxItem, items.get(1));
    perform(json(post("/prescriptions/" + id + "/dispense"), body), pharmacist)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    perform(get("/prescriptions/" + id), pharmacist)
        .andExpect(jsonPath("$.items[0].remaining").value(9));

    receive(paracetamol, "PA-1", LocalDate.now(ZoneId.of("Africa/Luanda")).plusDays(100), 20);
    perform(json(post("/prescriptions/" + id + "/dispense"), body), pharmacist)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISPENSED"));

    perform(json(post("/prescriptions/" + id + "/dispense"), dispense(amoxItem, 1)), pharmacist)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRESCRIPTION_DISPENSED"));
  }

  @Test
  void expiredPrescriptionCannotBeDispensed() throws Exception {
    String id =
        create(
            """
            {"episodeId":"%s","validityDays":1,"items":[%s]}"""
                .formatted(episodeId, item(amoxicillin, 2)));
    String item = items(id).get(0);
    clock.advance(Duration.ofDays(2));
    pharmacist = scenario.login(pharmacistUsername);

    perform(get("/prescriptions/" + id), pharmacist)
        .andExpect(jsonPath("$.effectiveStatus").value("EXPIRED"));
    perform(json(post("/prescriptions/" + id + "/dispense"), dispense(item, 1)), pharmacist)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRESCRIPTION_EXPIRED"));
  }

  @Test
  void cancelKeepsWhatWasDispensedAndBlocksTheRest() throws Exception {
    String id = create(prescription(21, 5));
    String item = items(id).get(0);
    perform(json(post("/prescriptions/" + id + "/dispense"), dispense(item, 7)), pharmacist)
        .andExpect(status().isOk());

    perform(json(patch("/prescriptions/" + id + "/cancel"), "{\"reason\":\"Alergia\"}"), pharmacist)
        .andExpect(status().isForbidden());
    perform(
            json(patch("/prescriptions/" + id + "/cancel"), "{\"reason\":\"Reacção alérgica\"}"),
            doctor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.items[0].quantityDispensed").value(7));
    perform(json(post("/prescriptions/" + id + "/dispense"), dispense(item, 1)), pharmacist)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRESCRIPTION_CANCELLED"));
  }

  @Test
  void pharmacyWorklistAndAudit() throws Exception {
    String id = create(prescription(5, 5));
    perform(get("/prescriptions").param("open", "true"), pharmacist)
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(id));

    List<String> actions =
        jdbc.queryForList(
            "SELECT action FROM audit_events WHERE entity_type = 'PRESCRIPTION' AND entity_id = ?",
            String.class,
            id);
    assertThat(actions).contains("CREATE");
  }

  @Test
  void concurrentDispensesNeverExceedPrescribedQuantity() throws Exception {
    String id = create(prescription(10, 1));
    String item = items(id).get(0);
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    List<Callable<Integer>> tasks = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      tasks.add(
          () -> {
            start.await();
            return mockMvc
                .perform(
                    json(post("/prescriptions/" + id + "/dispense"), dispense(item, 6))
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
    assertThat(statuses).containsExactlyInAnyOrder(200, 400);
    Integer dispensed =
        jdbc.queryForObject(
            "SELECT quantity_dispensed FROM prescription_items WHERE id = ?", Integer.class, item);
    assertThat(dispensed).isEqualTo(6);
  }

  // ---------- Auxiliares ----------

  private String prescription(int amoxQty, int paraQty) {
    return """
        {"episodeId":"%s","notes":"Tomar após as refeições","items":[%s,%s]}"""
        .formatted(episodeId, item(amoxicillin, amoxQty), item(paracetamol, paraQty));
  }

  private static String item(String med, int qty) {
    return """
        {"medicationId":"%s","quantity":%d,"dosage":"1 comprimido","frequency":"de 8 em 8 horas",
         "durationDays":7,"route":"ORAL"}"""
        .formatted(med, qty);
  }

  private static String dispense(String item, int qty) {
    return "{\"lines\":[{\"itemId\":\"" + item + "\",\"quantity\":" + qty + "}]}";
  }

  private String create(String body) throws Exception {
    return id(
        perform(json(post("/prescriptions"), body), doctor)
            .andExpect(status().isCreated())
            .andReturn());
  }

  private List<String> items(String prescriptionId) throws Exception {
    var result = perform(get("/prescriptions/" + prescriptionId), doctor).andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.items[*].id");
  }

  private String medication(String name, String strength, boolean rx) throws Exception {
    return id(
        perform(
                json(
                    post("/pharmacy/medications"),
                    """
                    {"name":"%s","dosageForm":"TABLET","strength":"%s","dispensingUnit":"comprimido",
                     "requiresPrescription":%s,"minStockLevel":0}"""
                        .formatted(name, strength, rx)),
                pharmacist)
            .andExpect(status().isCreated())
            .andReturn());
  }

  private void receive(String med, String batch, LocalDate expiry, int qty) throws Exception {
    perform(
            json(
                post("/pharmacy/stock/receive"),
                """
                {"medicationId":"%s","batchNumber":"%s","expiryDate":"%s","quantity":%d}"""
                    .formatted(med, batch, expiry, qty)),
            pharmacist)
        .andExpect(status().isCreated());
  }

  private static String id(MvcResult result) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private ResultActions perform(MockHttpServletRequestBuilder request, RequestPostProcessor as)
      throws Exception {
    return mockMvc.perform(request.with(as));
  }

  private static MockHttpServletRequestBuilder put_(String url) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url);
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder builder, String content) {
    return builder.contentType(MediaType.APPLICATION_JSON).content(content);
  }
}
