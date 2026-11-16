package com.ngolahealth.backend.modules.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ngolahealth.backend.modules.scheduling.application.AgendaReminderJob;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.support.AuthFixtures;
import com.ngolahealth.backend.support.MutableClock;
import com.ngolahealth.backend.support.TenantScenario;
import com.ngolahealth.backend.support.TestClockConfig;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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

/**
 * Horários, ausências, vagas, marcação com capacidade (incluindo concorrência), ciclo de vida,
 * remarcação, notificações e lembretes. O relógio fica às 07:00 (Luanda) de uma segunda-feira.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class SchedulingApiIntegrationTest {

  private static final ZoneId LUANDA = ZoneId.of("Africa/Luanda");

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;
  @Autowired private AgendaReminderJob reminderJob;

  private TenantScenario scenario;
  private UUID hospital;
  private LocalDate monday;
  private RequestPostProcessor admin;
  private RequestPostProcessor reception;
  private RequestPostProcessor doctor;
  private RequestPostProcessor otherDoctor;
  private String doctorId;
  private String otherDoctorId;
  private final List<String> patients = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    monday = LocalDate.now(LUANDA).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    clock.setTo(monday.atTime(7, 0).atZone(LUANDA).toInstant());
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospital = scenario.hospital("AG");
    admin = scenario.as("adm", hospital, Role.ADMIN);
    reception = scenario.as("rec", hospital, Role.RECEPTIONIST);
    doctor = scenario.as("med", hospital, Role.DOCTOR);
    otherDoctor = scenario.as("med2", hospital, Role.DOCTOR);
    doctorId = idOf("med");
    otherDoctorId = idOf("med2");
    for (var name : List.of("Ana Silva", "Rui Costa", "Eva Lopes")) {
      patients.add(
          id(
              perform(
                  json(
                      post("/patients"),
                      "{\"fullName\":\"%s\",\"birthDate\":\"1990-05-01\",\"gender\":\"FEMALE\",\"phone\":\"923000000\"}"
                          .formatted(name)),
                  reception)));
    }
    // Segunda, 08:00–12:00, vagas de 30 min com 2 lugares (8 vagas).
    perform(json(post("/scheduling/schedules"), schedule(doctorId, "08:00", "12:00", 30, 2)), admin)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slotsPerDay").value(8));
  }

  @AfterEach
  void cleanUp() {
    scenario.cleanUp();
    clock.reset();
  }

  @Test
  void scheduleRules() throws Exception {
    perform(json(post("/scheduling/schedules"), schedule(doctorId, "11:00", "13:00", 30, 1)), admin)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SCHEDULE_OVERLAP"));
    perform(json(post("/scheduling/schedules"), schedule(doctorId, "14:00", "13:00", 30, 1)), admin)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
    perform(json(post("/scheduling/schedules"), schedule(doctorId, "14:00", "14:20", 30, 1)), admin)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SLOT_LONGER_THAN_SCHEDULE"));
    perform(
            json(post("/scheduling/schedules"), schedule(idOf("rec"), "14:00", "16:00", 30, 1)),
            admin)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DOCTOR"));
    perform(
            json(post("/scheduling/schedules"), schedule(doctorId, "14:00", "16:00", 30, 1)),
            reception)
        .andExpect(status().isForbidden());
    perform(get("/scheduling/schedules").param("doctorId", doctorId), reception)
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"));
  }

  @Test
  void bookingRespectsCapacityBlocksAndPatientClash() throws Exception {
    String a1 =
        book(patients.get(0), doctorId, "09:00")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slotPosition").value(1))
            .andExpect(jsonPath("$.patientPhone").value("923000000"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String first = JsonPath.read(a1, "$.id");
    book(patients.get(1), doctorId, "09:00").andExpect(jsonPath("$.slotPosition").value(2));
    book(patients.get(2), doctorId, "09:00")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_FULL"));
    // O mesmo paciente não pode estar em dois sítios à mesma hora, mesmo com outro médico.
    perform(
        json(post("/scheduling/schedules"), schedule(otherDoctorId, "08:00", "12:00", 30, 1)),
        admin);
    book(patients.get(0), otherDoctorId, "09:00")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PATIENT_BUSY"));
    book(patients.get(2), doctorId, "09:10")
        .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
    book(patients.get(2), doctorId, "13:00")
        .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));

    // Ausência das 10:00 às 11:00: o médico só regista na própria agenda.
    perform(json(post("/scheduling/blocks"), block(otherDoctorId, "10:00", "11:00")), doctor)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("OWN_AGENDA_ONLY"));
    perform(json(post("/scheduling/blocks"), block(doctorId, "10:00", "11:00")), doctor)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.affectedAppointments.length()").value(0));
    book(patients.get(2), doctorId, "10:30")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_BLOCKED"));
    // Uma ausência sobre marcações existentes devolve-as para a recepção remarcar.
    perform(json(post("/scheduling/blocks"), block(doctorId, "09:00", "09:30")), admin)
        .andExpect(jsonPath("$.affectedAppointments.length()").value(2));

    perform(
            get("/scheduling/availability")
                .param("doctorId", doctorId)
                .param("date", monday.toString()),
            reception)
        .andExpect(jsonPath("$.slots.length()").value(8))
        .andExpect(jsonPath("$.slots[0].state").value("FREE"))
        .andExpect(jsonPath("$.slots[2].startTime").value("09:00:00"))
        .andExpect(jsonPath("$.slots[2].booked").value(2))
        .andExpect(jsonPath("$.slots[4].state").value("BLOCKED"));

    // Cancelar liberta o lugar.
    perform(
            json(
                patch("/scheduling/appointments/" + first + "/cancel"),
                "{\"reason\":\"Pedido da paciente\"}"),
            reception)
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    perform(json(post("/scheduling/blocks"), "{}"), admin).andExpect(status().isBadRequest());
    String blockId =
        JsonPath.read(
            perform(get("/scheduling/blocks").param("doctorId", doctorId), admin)
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$[0].id"); // a ausência das 09:00
    perform(delete("/scheduling/blocks/" + blockId), admin).andExpect(status().isNoContent());
    book(patients.get(2), doctorId, "09:00").andExpect(jsonPath("$.slotPosition").value(1));
  }

  @Test
  void concurrentBookingsNeverExceedCapacity() throws Exception {
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(3);
    List<Callable<Integer>> tasks = new ArrayList<>();
    for (var patient : patients) {
      tasks.add(
          () -> {
            start.await();
            return book(patient, doctorId, "08:30").andReturn().getResponse().getStatus();
          });
    }
    var futures = tasks.stream().map(pool::submit).toList();
    start.countDown();
    List<Integer> statuses = new ArrayList<>();
    for (var f : futures) {
      statuses.add(f.get());
    }
    pool.shutdown();
    assertThat(statuses).containsExactlyInAnyOrder(201, 201, 409);
    assertThat(
            jdbc.queryForList(
                "SELECT slot_position FROM appointments WHERE doctor_id = ? ORDER BY slot_position",
                Integer.class,
                doctorId))
        .containsExactly(1, 2);
  }

  @Test
  void lifecycleWithCheckInOpeningTheEpisode() throws Exception {
    String a = id(book(patients.get(0), doctorId, "09:00"));
    String b = id(book(patients.get(1), doctorId, "09:00"));
    perform(patch("/scheduling/appointments/" + a + "/confirm"), reception)
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    perform(patch("/scheduling/appointments/" + a + "/confirm"), reception)
        .andExpect(status().isConflict());
    perform(patch("/scheduling/appointments/" + b + "/no-show"), reception)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TOO_EARLY_FOR_NO_SHOW"));

    clock.advance(Duration.ofHours(2).plusMinutes(5)); // 09:05
    String body =
        perform(patch("/scheduling/appointments/" + a + "/check-in"), reception)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHECKED_IN"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String episodeId = JsonPath.read(body, "$.episodeId");
    perform(get("/episodes/" + episodeId), doctor)
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.type").value("OUTPATIENT"))
        .andExpect(jsonPath("$.doctorId").value(doctorId));
    perform(
            json(patch("/scheduling/appointments/" + a + "/cancel"), "{\"reason\":\"x\"}"),
            reception)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_APPOINTMENT_STATUS"));
    perform(patch("/scheduling/appointments/" + b + "/no-show"), reception)
        .andExpect(jsonPath("$.status").value("NO_SHOW"));

    // Uma marcação noutro dia não admite chegada hoje.
    String next = id(book(patients.get(2), doctorId, "08:00", monday.plusWeeks(1)));
    perform(patch("/scheduling/appointments/" + next + "/check-in"), reception)
        .andExpect(jsonPath("$.code").value("NOT_APPOINTMENT_DAY"));
    List<String> actions =
        jdbc.queryForList(
            "SELECT action FROM audit_events WHERE entity_type = 'APPOINTMENT' AND entity_id = ?",
            String.class,
            a);
    assertThat(actions).contains("CREATE", "CONFIRM", "CHECK_IN");
  }

  @Test
  void rescheduleIsAtomicAndDoctorsAreNotified() throws Exception {
    perform(
        json(post("/scheduling/schedules"), schedule(otherDoctorId, "08:00", "12:00", 30, 1)),
        admin);
    String a = id(book(patients.get(0), doctorId, "09:00"));
    id(book(patients.get(1), otherDoctorId, "10:00"));

    // Vaga cheia: a remarcação falha e a marcação original mantém-se.
    perform(
            json(
                post("/scheduling/appointments/" + a + "/reschedule"),
                "{\"doctorId\":\"%s\",\"date\":\"%s\",\"startTime\":\"10:00\"}"
                    .formatted(otherDoctorId, monday)),
            reception)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_FULL"));
    perform(get("/scheduling/appointments/" + a), reception)
        .andExpect(jsonPath("$.status").value("SCHEDULED"));

    String moved =
        perform(
                json(
                    post("/scheduling/appointments/" + a + "/reschedule"),
                    "{\"doctorId\":\"%s\",\"date\":\"%s\",\"startTime\":\"11:00\",\"reason\":\"Médico ausente\"}"
                        .formatted(otherDoctorId, monday)),
                reception)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rescheduledFromId").value(a))
            .andExpect(jsonPath("$.doctorId").value(otherDoctorId))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newId = JsonPath.read(moved, "$.id");
    perform(get("/scheduling/appointments/" + a), reception)
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.rescheduledToId").value(newId));
    perform(get("/notifications"), doctor)
        .andExpect(jsonPath("$.content[0].type").value("APPOINTMENT_RESCHEDULED"));
    perform(get("/notifications"), otherDoctor)
        .andExpect(jsonPath("$.content[0].type").value("APPOINTMENT_RESCHEDULED"));

    perform(
        json(patch("/scheduling/appointments/" + newId + "/cancel"), "{\"reason\":\"Doente\"}"),
        reception);
    perform(get("/notifications"), otherDoctor)
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(
            jsonPath("$.content[?(@.type=='APPOINTMENT_CANCELLED')].message")
                .value(
                    org.hamcrest.Matchers.contains(
                        org.hamcrest.Matchers.containsString("Motivo: Doente"))));
  }

  @Test
  void eveningReminderSummarisesTomorrowsAgenda() throws Exception {
    book(patients.get(0), doctorId, "08:00");
    book(patients.get(1), doctorId, "10:30");
    clock.setTo(monday.minusDays(1).atTime(18, 0).atZone(LUANDA).toInstant());
    assertThat(reminderJob.run()).isGreaterThanOrEqualTo(1);
    reminderJob.run();
    perform(get("/notifications"), doctor)
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].type").value("AGENDA_REMINDER"))
        .andExpect(jsonPath("$.content[0].message").value("2 consultas, das 08:00 às 10:30."));
    perform(get("/notifications/unread-count"), otherDoctor)
        .andExpect(jsonPath("$.count").value(0));
  }

  @Test
  void monthCalendarAndPastSlots() throws Exception {
    book(patients.get(0), doctorId, "09:00");
    perform(
            get("/scheduling/calendar")
                .param("month", monday.toString().substring(0, 7))
                .param("doctorId", doctorId),
            reception)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.date=='" + monday + "')].totalSlots")
                .value(org.hamcrest.Matchers.contains(8)))
        .andExpect(
            jsonPath("$[?(@.date=='" + monday + "')].appointments")
                .value(org.hamcrest.Matchers.contains(1)));
    clock.advance(Duration.ofHours(2)); // 09:00: as vagas até às 09:00 já passaram
    book(patients.get(1), doctorId, "09:00")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_IN_PAST"));
    perform(
            get("/scheduling/appointments")
                .param("from", monday.toString())
                .param("doctorId", doctorId),
            reception)
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].patientName").value("Ana Silva"));
  }

  // ---------- Auxiliares ----------

  private ResultActions book(String patient, String doctor, String time) throws Exception {
    return book(patient, doctor, time, monday);
  }

  private ResultActions book(String patient, String doctor, String time, LocalDate date)
      throws Exception {
    return perform(
        json(
            post("/scheduling/appointments"),
            """
            {"patientId":"%s","doctorId":"%s","type":"CONSULTATION","date":"%s","startTime":"%s","reason":"Consulta de rotina"}"""
                .formatted(patient, doctor, date, time)),
        reception);
  }

  private static String schedule(String doctor, String start, String end, int slot, int capacity) {
    return """
        {"doctorId":"%s","dayOfWeek":"MONDAY","startTime":"%s","endTime":"%s","slotMinutes":%d,"capacity":%d}"""
        .formatted(doctor, start, end, slot, capacity);
  }

  private String block(String doctor, String start, String end) {
    return """
        {"doctorId":"%s","date":"%s","startTime":"%s","endTime":"%s","reason":"Formação"}"""
        .formatted(doctor, monday, start, end);
  }

  private String idOf(String name) {
    return jdbc.queryForObject(
        "SELECT id FROM users WHERE username LIKE ? AND hospital_id = ?",
        String.class,
        name + ".%",
        hospital.toString());
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

  @SuppressWarnings("unused")
  private static LocalTime t(String s) {
    return LocalTime.parse(s);
  }
}
