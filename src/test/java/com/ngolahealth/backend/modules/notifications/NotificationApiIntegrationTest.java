package com.ngolahealth.backend.modules.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ngolahealth.backend.modules.notifications.application.NotificationCleanupJob;
import com.ngolahealth.backend.modules.notifications.application.NotificationSender;
import com.ngolahealth.backend.modules.notifications.domain.NotificationContent;
import com.ngolahealth.backend.modules.notifications.domain.NotificationPriority;
import com.ngolahealth.backend.modules.notifications.domain.NotificationType;
import com.ngolahealth.backend.modules.pharmacy.application.StockAlertJob;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Notificações do laboratório e do stock, leitura individual, isolamento e retenção. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class NotificationApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;
  @Autowired private StockAlertJob stockAlertJob;
  @Autowired private NotificationSender sender;
  @Autowired private NotificationCleanupJob cleanupJob;

  private TenantScenario scenario;
  private UUID hospital;

  @BeforeEach
  void setUp() {
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospital = scenario.hospital("NT");
  }

  @AfterEach
  void cleanUp() {
    scenario.cleanUp();
    clock.reset();
  }

  @Test
  void labResultsNotifyTheRequestingDoctorOnly() throws Exception {
    var reception = scenario.as("rec", hospital, Role.RECEPTIONIST);
    var nurse = scenario.as("enf", hospital, Role.NURSE);
    var doctor = scenario.as("med", hospital, Role.DOCTOR);
    var lab = scenario.as("lab", hospital, Role.LAB_TECHNICIAN);
    String patientId =
        id(
            perform(
                json(
                    post("/patients"),
                    "{\"fullName\":\"Ana Silva\",\"birthDate\":\"1990-05-01\",\"gender\":\"FEMALE\"}"),
                reception));
    String episodeId =
        id(
            perform(
                json(
                    post("/episodes"),
                    "{\"patientId\":\""
                        + patientId
                        + "\",\"type\":\"OUTPATIENT\",\"reason\":\"Febre\"}"),
                reception));
    perform(patch("/episodes/" + episodeId + "/start"), nurse).andExpect(status().isOk());
    String hb =
        labTest(
            lab,
            "HB",
            "Hemoglobina",
            "NUMERIC",
            "\"unit\":\"g/dL\",\"referenceLow\":12,\"referenceHigh\":16");
    String ge = labTest(lab, "GE", "Gota espessa", "QUALITATIVE", "\"referenceText\":\"Negativo\"");
    String requestId =
        id(
            perform(
                json(
                    post("/lab/requests"),
                    "{\"episodeId\":\"%s\",\"priority\":\"URGENT\",\"testIds\":[\"%s\",\"%s\"]}"
                        .formatted(episodeId, hb, ge)),
                doctor));
    var body =
        perform(get("/lab/requests/" + requestId), lab)
            .andReturn()
            .getResponse()
            .getContentAsString();
    String hbItem = JsonPath.<List<String>>read(body, "$.items[?(@.testCode=='HB')].id").getFirst();
    String geItem = JsonPath.<List<String>>read(body, "$.items[?(@.testCode=='GE')].id").getFirst();
    perform(json(post("/lab/requests/" + requestId + "/collect"), "{}"), nurse);
    perform(post("/lab/requests/" + requestId + "/start"), lab);

    // Resultado alterado validado com o pedido ainda em curso → aviso imediato.
    perform(json(put(itemUrl(requestId, hbItem) + "/result"), "{\"numericValue\":10.5}"), lab);
    perform(post(itemUrl(requestId, hbItem) + "/validate"), lab).andExpect(status().isOk());
    perform(get("/notifications"), doctor)
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].type").value("LAB_ABNORMAL_RESULT"))
        .andExpect(jsonPath("$.content[0].priority").value("HIGH"))
        .andExpect(
            jsonPath("$.content[0].message")
                .value(org.hamcrest.Matchers.containsString("10.5 g/dL (baixo)")))
        .andExpect(jsonPath("$.content[0].link").value("/lab/requests/" + requestId));

    // Último exame validado → pedido concluído → resultados disponíveis.
    clock.advance(Duration.ofSeconds(5));
    perform(
        json(
            put(itemUrl(requestId, geItem) + "/result"),
            "{\"textValue\":\"Positivo\",\"abnormal\":true}"),
        lab);
    perform(post(itemUrl(requestId, geItem) + "/validate"), lab)
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // Correcção de um resultado validado → aviso com valor anterior, novo e motivo.
    clock.advance(Duration.ofSeconds(5));
    perform(
            json(
                post(itemUrl(requestId, hbItem) + "/amend"),
                "{\"numericValue\":13,\"reason\":\"Erro de transcrição\"}"),
            lab)
        .andExpect(status().isOk());

    perform(get("/notifications"), doctor)
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].type").value("LAB_RESULT_AMENDED"))
        .andExpect(
            jsonPath("$.content[0].message")
                .value(org.hamcrest.Matchers.containsString("10.5 g/dL (baixo) → 13 g/dL")))
        .andExpect(jsonPath("$.content[1].type").value("LAB_RESULTS_READY"))
        .andExpect(jsonPath("$.content[1].priority").value("HIGH"))
        .andExpect(
            jsonPath("$.content[1].message")
                .value(org.hamcrest.Matchers.containsString("2 exames validados, 2 alterados")));
    perform(get("/notifications/unread-count"), doctor).andExpect(jsonPath("$.count").value(3));
    perform(get("/notifications/unread-count"), lab).andExpect(jsonPath("$.count").value(0));
    perform(get("/notifications/unread-count"), nurse).andExpect(jsonPath("$.count").value(0));
  }

  @Test
  void readStateIsPerUserAndPrivate() throws Exception {
    var ana = scenario.as("ana", hospital, Role.DOCTOR);
    var rui = scenario.as("rui", hospital, Role.DOCTOR);
    UUID anaId = idOf("ana");
    UUID ruiId = idOf("rui");
    assertThat(sender.send(hospital, List.of(anaId, ruiId), content("Aviso 1", null))).isEqualTo(2);
    clock.advance(Duration.ofSeconds(1));
    sender.send(hospital, List.of(anaId), content("Aviso 2", null));

    String anaFirst =
        JsonPath.read(
            perform(get("/notifications"), ana).andReturn().getResponse().getContentAsString(),
            "$.content[1].id");
    // O Rui não pode marcar (nem saber da existência de) uma notificação da Ana.
    perform(patch("/notifications/" + anaFirst + "/read"), rui).andExpect(status().isNotFound());
    perform(patch("/notifications/" + anaFirst + "/read"), ana)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readAt").exists());
    perform(get("/notifications/unread-count"), ana).andExpect(jsonPath("$.count").value(1));
    // Ler a sua cópia não altera a do Rui (no modelo, as notificações "para todos" partilhavam o
    // mesmo estado).
    perform(get("/notifications/unread-count"), rui).andExpect(jsonPath("$.count").value(1));
    perform(get("/notifications").param("unreadOnly", "true"), ana)
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].title").value("Aviso 2"));
    perform(patch("/notifications/read-all"), ana).andExpect(jsonPath("$.count").value(0));
    perform(get("/notifications/unread-count"), rui).andExpect(jsonPath("$.count").value(1));
    // Marcar como lido não é um dado clínico: não gera auditoria genérica.
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE hospital_id = ? AND action = 'REQUEST'",
                Integer.class,
                hospital.toString()))
        .isZero();
  }

  @Test
  void onlyActiveUsersOfTheHospitalReceive() throws Exception {
    UUID other = scenario.hospital("NX");
    UUID outsider = scenario.userId(scenario.user("fora", other, Role.DOCTOR));
    UUID inactive = scenario.userId(scenario.user("inactivo", hospital, Role.DOCTOR));
    jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE id = ?", inactive.toString());
    assertThat(sender.send(hospital, List.of(outsider, inactive), content("X", null))).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE hospital_id IN (?, ?)",
                Integer.class,
                hospital.toString(),
                other.toString()))
        .isZero();
  }

  @Test
  void dailyStockSummaryGoesToPharmacistsOnceADay() throws Exception {
    var pharmacist = scenario.as("farm", hospital, Role.PHARMACIST);
    var admin = scenario.as("adm", hospital, Role.ADMIN);
    perform(
        json(
            post("/pharmacy/medications"),
            """
            {"name":"Paracetamol","dosageForm":"TABLET","strength":"500 mg",
             "dispensingUnit":"comprimido","requiresPrescription":false,"minStockLevel":10}"""),
        pharmacist);

    assertThat(stockAlertJob.run()).isGreaterThanOrEqualTo(1);
    stockAlertJob.run(); // mesmo dia: não repete
    perform(get("/notifications"), pharmacist)
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].type").value("STOCK_ALERTS"))
        .andExpect(jsonPath("$.content[0].priority").value("CRITICAL"))
        .andExpect(
            jsonPath("$.content[0].message")
                .value(org.hamcrest.Matchers.containsString("Paracetamol")))
        .andExpect(jsonPath("$.content[0].link").value("/pharmacy/alerts"));
    perform(get("/notifications/unread-count"), admin).andExpect(jsonPath("$.count").value(0));

    // Sem farmacêuticos activos, o resumo vai para os administradores.
    jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE id = ?", idOf("farm").toString());
    clock.advance(Duration.ofDays(1));
    stockAlertJob.run();
    perform(get("/notifications/unread-count"), scenario.login(usernameOf("adm")))
        .andExpect(jsonPath("$.count").value(1));
  }

  @Test
  void oldNotificationsArePurged() {
    UUID user = scenario.userId(scenario.user("velho", hospital, Role.NURSE));
    sender.send(hospital, List.of(user), content("Antiga", null));
    sender.send(hospital, List.of(user), content("Recente", null));
    jdbc.update(
        "UPDATE notifications SET created_at = DATE_SUB(created_at, INTERVAL 91 DAY)"
            + " WHERE recipient_id = ? AND title = 'Antiga'",
        user.toString());
    assertThat(cleanupJob.purge()).isGreaterThanOrEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "SELECT title FROM notifications WHERE recipient_id = ?",
                String.class,
                user.toString()))
        .containsExactly("Recente");
  }

  @Test
  void usersWithoutHospitalHaveNoNotifications() throws Exception {
    var platform = scenario.as("plataforma", null, Role.SUPER_ADMIN);
    perform(get("/notifications/unread-count"), platform).andExpect(jsonPath("$.count").value(0));
    perform(get("/notifications"), platform).andExpect(jsonPath("$.totalElements").value(0));
    mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
  }

  // ---------- Auxiliares ----------

  private static NotificationContent content(String title, String dedupeKey) {
    return new NotificationContent(
        NotificationType.SYSTEM,
        NotificationPriority.NORMAL,
        title,
        "Mensagem de teste",
        "/",
        null,
        null,
        dedupeKey);
  }

  private String usernameOf(String name) {
    return jdbc.queryForObject(
        "SELECT username FROM users WHERE username LIKE ? AND hospital_id = ?",
        String.class,
        name + ".%",
        hospital.toString());
  }

  private UUID idOf(String name) {
    return scenario.userId(usernameOf(name));
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

  private static String itemUrl(String requestId, String itemId) {
    return "/lab/requests/" + requestId + "/items/" + itemId;
  }

  private String id(ResultActions result) throws Exception {
    MvcResult r = result.andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
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
