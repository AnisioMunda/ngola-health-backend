package com.ngolahealth.backend.modules.hr;

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
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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
 * Escala (sobreposições, turno da noite, cópia de semana), ausências (aprovação, conflitos com a
 * escala) e ponto (atraso, horas extra, turno que atravessa a meia-noite, correcção). O relógio
 * está às 07:00 (Luanda) de uma segunda-feira.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class HrApiIntegrationTest {

  private static final ZoneId LUANDA = ZoneId.of("Africa/Luanda");

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;

  private TenantScenario scenario;
  private UUID hospital;
  private LocalDate monday;
  private RequestPostProcessor manager;
  private RequestPostProcessor nurse;
  private RequestPostProcessor doctor;
  private String nurseId;
  private String doctorId;

  @BeforeEach
  void setUp() throws Exception {
    monday = LocalDate.now(LUANDA).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    clock.setTo(monday.atTime(7, 0).atZone(LUANDA).toInstant());
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospital = scenario.hospital("RH");
    manager = scenario.as("ger", hospital, Role.MANAGER);
    nurse = scenario.as("enf", hospital, Role.NURSE);
    doctor = scenario.as("med", hospital, Role.DOCTOR);
    nurseId = idOf("enf");
    doctorId = idOf("med");
  }

  @AfterEach
  void cleanUp() {
    scenario.cleanUp();
    clock.reset();
  }

  @Test
  void shiftRulesAndNightShiftCrossingMidnight() throws Exception {
    shift(nurseId, "NIGHT", monday, "20:00", "08:00")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.date").value(monday.toString()))
        .andExpect(jsonPath("$.endsAt").value(Matchers.startsWith(monday.plusDays(1).toString())));
    shift(nurseId, "MORNING", monday.plusDays(1), "06:00", "14:00")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SHIFT_OVERLAP"));
    shift(nurseId, "MORNING", monday.minusDays(3), "08:00", "16:00")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SHIFT_IN_PAST"));
    perform(
            json(
                post("/hr/shifts"),
                shiftBody(nurseId, "MORNING", monday.plusDays(2), "08:00", "16:00")),
            doctor)
        .andExpect(status().isForbidden());
    UUID other = scenario.hospital("RX");
    String outsider = scenario.userId(scenario.user("fora", other, Role.NURSE)).toString();
    shift(outsider, "MORNING", monday.plusDays(2), "08:00", "16:00")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_STAFF"));
    // Todos vêem a escala; o próprio vê os seus turnos.
    perform(
            get("/hr/shifts")
                .param("from", monday.toString())
                .param("to", monday.plusDays(6).toString()),
            doctor)
        .andExpect(jsonPath("$.length()").value(1));
    perform(
            get("/hr/shifts/mine")
                .param("from", monday.toString())
                .param("to", monday.plusDays(6).toString()),
            nurse)
        .andExpect(jsonPath("$[0].type").value("NIGHT"));
  }

  @Test
  void leaveApprovalCancelsConflictingShiftsOnlyWhenConfirmed() throws Exception {
    String thursday = id(shift(nurseId, "MORNING", monday.plusDays(3), "08:00", "16:00"));
    String leave =
        id(
            perform(
                json(
                    post("/hr/leaves"),
                    leaveBody("VACATION", monday.plusDays(2), monday.plusDays(4))),
                nurse));
    perform(
            json(post("/hr/leaves"), leaveBody("PERSONAL", monday.plusDays(4), monday.plusDays(5))),
            nurse)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LEAVE_OVERLAP"));
    perform(get("/notifications"), manager)
        .andExpect(jsonPath("$.content[0].type").value("LEAVE_REQUESTED"));

    perform(json(post("/hr/leaves/" + leave + "/decision"), "{\"approve\":true}"), nurse)
        .andExpect(status().isForbidden());
    perform(json(post("/hr/leaves/" + leave + "/decision"), "{\"approve\":true}"), manager)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LEAVE_SHIFT_CONFLICT"));
    perform(
            json(
                post("/hr/leaves/" + leave + "/decision"),
                "{\"approve\":true,\"cancelConflictingShifts\":true,\"note\":\"Boas férias\"}"),
            manager)
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.days").value(3));
    assertThat(
            jdbc.queryForObject("SELECT status FROM shifts WHERE id = ?", String.class, thursday))
        .isEqualTo("CANCELLED");
    perform(get("/notifications"), nurse)
        .andExpect(jsonPath("$.content[0].type").value("LEAVE_DECIDED"))
        .andExpect(jsonPath("$.content[0].message").value("Boas férias"));
    shift(nurseId, "MORNING", monday.plusDays(3), "08:00", "16:00")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USER_ON_LEAVE"));

    // O gestor não decide o próprio pedido.
    String own =
        id(
            perform(
                json(
                    post("/hr/leaves"),
                    leaveBody("TRAINING", monday.plusDays(10), monday.plusDays(10))),
                manager));
    perform(json(post("/hr/leaves/" + own + "/decision"), "{\"approve\":true}"), manager)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("OWN_LEAVE"));
    perform(patch("/hr/leaves/" + leave + "/cancel"), nurse)
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void attendanceComputesLatenessOvertimeAndDailyPresence() throws Exception {
    shift(nurseId, "MORNING", monday, "08:00", "16:00");
    shift(doctorId, "MORNING", monday, "08:00", "16:00");
    String absentee = scenario.userId(scenario.user("aux", hospital, Role.NURSE)).toString();
    shift(absentee, "MORNING", monday, "08:00", "16:00");

    perform(post("/hr/attendance/clock-in"), nurse)
        .andExpect(jsonPath("$.shiftId").exists())
        .andExpect(jsonPath("$.lateMinutes").value(0));
    perform(post("/hr/attendance/clock-in"), nurse)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_CLOCKED_IN"));
    clock.advance(Duration.ofMinutes(80)); // 08:20
    perform(post("/hr/attendance/clock-in"), doctor).andExpect(jsonPath("$.lateMinutes").value(20));

    perform(get("/hr/attendance/daily"), manager)
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(
            jsonPath("$[?(@.shift.userId=='" + nurseId + "')].state")
                .value(Matchers.contains("ON_DUTY")))
        .andExpect(
            jsonPath("$[?(@.shift.userId=='" + doctorId + "')].state")
                .value(Matchers.contains("LATE")))
        .andExpect(
            jsonPath("$[?(@.shift.userId=='" + absentee + "')].state")
                .value(Matchers.contains("ABSENT")));
    perform(get("/hr/overview"), manager)
        .andExpect(jsonPath("$.shiftsToday").value(3))
        .andExpect(jsonPath("$.absentToday").value(1))
        .andExpect(jsonPath("$.lateToday").value(1));

    clock.advance(Duration.ofMinutes(490)); // 16:30
    String record =
        JsonPath.read(
            perform(post("/hr/attendance/clock-out"), nurse)
                .andExpect(jsonPath("$.minutesWorked").value(570))
                .andExpect(jsonPath("$.overtimeMinutes").value(30))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    perform(post("/hr/attendance/clock-out"), nurse)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOT_CLOCKED_IN"));

    // Correcção: só por um gestor, com motivo, e nunca no próprio registo.
    var correctedIn = monday.atTime(7, 55).atZone(LUANDA).toInstant();
    var correctedOut = monday.atTime(16, 0).atZone(LUANDA).toInstant();
    perform(
            json(
                patch("/hr/attendance/" + record),
                "{\"clockIn\":\"%s\",\"clockOut\":\"%s\",\"reason\":\"Esqueceu-se\"}"
                    .formatted(correctedIn, correctedOut)),
            nurse)
        .andExpect(status().isForbidden());
    perform(
            json(
                patch("/hr/attendance/" + record),
                "{\"clockIn\":\"%s\",\"clockOut\":\"%s\",\"reason\":\"Picou tarde\"}"
                    .formatted(correctedIn, correctedOut)),
            manager)
        .andExpect(jsonPath("$.minutesWorked").value(485))
        .andExpect(jsonPath("$.overtimeMinutes").value(0))
        .andExpect(jsonPath("$.correctionReason").value("Picou tarde"));
  }

  @Test
  void nightShiftClockOutTheNextMorning() throws Exception {
    shift(nurseId, "NIGHT", monday, "20:00", "08:00");
    clock.advance(Duration.ofHours(13)); // 20:00 — sessão nova (as sessões duram 12 h)
    var night = scenario.login(usernameOf("enf"));
    perform(post("/hr/attendance/clock-in"), night).andExpect(jsonPath("$.lateMinutes").value(0));
    clock.advance(Duration.ofHours(11)); // 07:00 de terça
    night = scenario.login(usernameOf("enf"));
    clock.advance(Duration.ofMinutes(65)); // 08:05
    perform(post("/hr/attendance/clock-out"), night)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.minutesWorked").value(725))
        .andExpect(jsonPath("$.overtimeMinutes").value(5));
  }

  @Test
  void clockInAndOutWithoutAShift() throws Exception {
    perform(post("/hr/attendance/clock-in"), doctor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shiftId").doesNotExist())
        .andExpect(jsonPath("$.lateMinutes").value(0));
    perform(get("/hr/attendance/current"), doctor).andExpect(status().isOk());
    clock.advance(Duration.ofMinutes(90));
    perform(post("/hr/attendance/clock-out"), doctor)
        .andExpect(jsonPath("$.minutesWorked").value(90))
        .andExpect(jsonPath("$.overtimeMinutes").value(0));
    perform(get("/hr/attendance/current"), doctor).andExpect(status().isNoContent());
  }

  @Test
  void copyWeekSkipsConflicts() throws Exception {
    shift(nurseId, "MORNING", monday, "08:00", "16:00");
    shift(doctorId, "AFTERNOON", monday.plusDays(1), "14:00", "22:00");
    shift(doctorId, "MORNING", monday.plusDays(8), "13:00", "15:00"); // choca com a cópia de terça
    perform(
            json(
                post("/hr/shifts/copy-week"),
                "{\"fromWeek\":\"%s\",\"toWeek\":\"%s\"}"
                    .formatted(monday.plusDays(2), monday.plusDays(7))),
            manager)
        .andExpect(jsonPath("$.created").value(1))
        .andExpect(jsonPath("$.skipped.length()").value(1))
        .andExpect(jsonPath("$.skipped[0]").value(Matchers.containsString("já tem turno")));
  }

  // ---------- Auxiliares ----------

  private ResultActions shift(String user, String type, LocalDate date, String start, String end)
      throws Exception {
    return perform(json(post("/hr/shifts"), shiftBody(user, type, date, start, end)), manager);
  }

  private static String shiftBody(
      String user, String type, LocalDate date, String start, String end) {
    return """
        {"userId":"%s","type":"%s","date":"%s","startTime":"%s","endTime":"%s"}"""
        .formatted(user, type, date, start, end);
  }

  private static String leaveBody(String type, LocalDate start, LocalDate end) {
    return """
        {"type":"%s","startDate":"%s","endDate":"%s","reason":"Pessoal"}"""
        .formatted(type, start, end);
  }

  private String usernameOf(String name) {
    return jdbc.queryForObject(
        "SELECT username FROM users WHERE username LIKE ? AND hospital_id = ?",
        String.class,
        name + ".%",
        hospital.toString());
  }

  private String idOf(String name) {
    return scenario.userId(usernameOf(name)).toString();
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
