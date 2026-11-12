package com.ngolahealth.backend.modules.patients;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Pacientes ponta a ponta. Sem {@code @Transactional}: cada pedido abre a sua sessão Hibernate,
 * como em produção, o que é necessário para o isolamento automático por hospital
 * ({@code @TenantId}) ser exercido de verdade. Os dados criados são apagados no fim de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;

  private TenantScenario scenario;
  private UUID hospitalA;
  private UUID hospitalB;
  private RequestPostProcessor receptionistA;
  private RequestPostProcessor receptionistB;

  @BeforeEach
  void setUp() throws Exception {
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospitalA = scenario.hospital("PA");
    hospitalB = scenario.hospital("PB");
    receptionistA = scenario.as("rec.a", hospitalA, Role.RECEPTIONIST);
    receptionistB = scenario.as("rec.b", hospitalB, Role.RECEPTIONIST);
  }

  @AfterEach
  void cleanUp() {
    scenario.cleanUp();
  }

  @Test
  void registersPatientWithSequentialRecordNumberPerHospital() throws Exception {
    String first = create(receptionistA, patient("Ana Silva", "1990-05-01", null));
    create(receptionistA, patient("Bruno Costa", "1985-01-10", null));
    String other = create(receptionistB, patient("Carla Dias", "1970-03-03", null));

    mockMvc
        .perform(get("/patients/" + first).with(receptionistA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recordNumber").value("000001"))
        .andExpect(jsonPath("$.age").isNumber());
    mockMvc
        .perform(get("/patients").param("search", "Bruno").with(receptionistA))
        .andExpect(jsonPath("$.content[0].recordNumber").value("000002"));
    // Cada hospital tem a sua numeração.
    mockMvc
        .perform(get("/patients/" + other).with(receptionistB))
        .andExpect(jsonPath("$.recordNumber").value("000001"));
  }

  @Test
  void patientsAreIsolatedBetweenHospitals() throws Exception {
    String id = create(receptionistA, patient("Ana Silva", "1990-05-01", null));

    mockMvc
        .perform(get("/patients").with(receptionistB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));
    mockMvc
        .perform(get("/patients/" + id).with(receptionistB))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    mockMvc
        .perform(
            json(put("/patients/" + id), patient("Roubado", "1990-05-01", null))
                .with(receptionistB))
        .andExpect(status().isNotFound());

    Integer stored =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM patients WHERE id = ? AND hospital_id = ?",
            Integer.class,
            id,
            hospitalA.toString());
    assertThat(stored).isEqualTo(1);
  }

  @Test
  void nationalIdIsUniqueWithinHospitalOnly() throws Exception {
    create(receptionistA, patient("Ana Silva", "1990-05-01", "004468681LA042"));

    mockMvc
        .perform(
            json(post("/patients"), patient("Outra Pessoa", "1991-01-01", "004468681la042"))
                .with(receptionistA))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PATIENT_IDENTIFIER_IN_USE"));
    create(receptionistB, patient("Ana Silva", "1990-05-01", "004468681LA042"));
  }

  @Test
  void findsPossibleDuplicatesIgnoringAccentsAndMiddleNames() throws Exception {
    String id = create(receptionistA, patient("Ána Maria Silva", "1990-05-01", null));
    create(receptionistB, patient("Ana Silva", "1990-05-01", null));

    mockMvc
        .perform(
            json(
                    post("/patients/possible-duplicates"),
                    "{\"fullName\":\"ana silva\",\"birthDate\":\"1990-05-01\"}")
                .with(receptionistA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(id));
    mockMvc
        .perform(get("/patients").param("search", "ANA MARIA").with(receptionistA))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void rolePermissions() throws Exception {
    String id = create(receptionistA, patient("Ana Silva", "1990-05-01", null));
    var doctor = login(user("med", hospitalA, Role.DOCTOR));
    var manager = login(user("gestor", hospitalA, Role.MANAGER));
    var pharmacist = login(user("farm", hospitalA, Role.PHARMACIST));
    var admin = login(user("admin", hospitalA, Role.ADMIN));
    var root = login(user("root", null, Role.SUPER_ADMIN));

    mockMvc.perform(get("/patients/" + id).with(doctor)).andExpect(status().isOk());
    mockMvc.perform(get("/patients").with(manager)).andExpect(status().isOk());
    mockMvc
        .perform(json(post("/patients"), patient("X Y", "2000-01-01", null)).with(manager))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/patients").with(pharmacist)).andExpect(status().isForbidden());
    mockMvc.perform(get("/patients").with(root)).andExpect(status().isForbidden());
    mockMvc
        .perform(patch("/patients/" + id + "/deactivate").with(receptionistA))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(patch("/patients/" + id + "/deactivate").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    mockMvc
        .perform(get("/patients").param("active", "true").with(admin))
        .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));
  }

  @Test
  void viewingAndEditingAreAudited() throws Exception {
    String id = create(receptionistA, patient("Ana Silva", "1990-05-01", null));
    mockMvc.perform(get("/patients/" + id).with(receptionistA)).andExpect(status().isOk());

    var body =
        patient("Ana Silva Costa", "1990-05-01", null).replace("\"version\":null", "\"version\":0");
    mockMvc
        .perform(json(put("/patients/" + id), body).with(receptionistA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mockMvc
        .perform(json(put("/patients/" + id), body).with(receptionistA))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

    List<String> actions =
        jdbc.queryForList(
            "SELECT action FROM audit_events WHERE entity_type = 'PATIENT' AND entity_id = ?"
                + " ORDER BY occurred_at",
            String.class,
            id);
    assertThat(actions).containsExactly("CREATE", "VIEW", "UPDATE");
    String changes =
        jdbc.queryForObject(
            "SELECT changes FROM audit_events WHERE entity_id = ? AND action = 'UPDATE'",
            String.class,
            id);
    assertThat(changes).contains("fullName").contains("Ana Silva Costa");
  }

  @Test
  void validationMessages() throws Exception {
    mockMvc
        .perform(
            json(
                    post("/patients"),
                    """
                    {"fullName":"A","birthDate":"2999-01-01","gender":"FEMALE",
                     "nationalId":"123","bloodType":"C+","phone":"abc"}""")
                .with(receptionistA))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.fullName").exists())
        .andExpect(
            jsonPath("$.errors.birthDate")
                .value("A data de nascimento deve ser anterior à data actual."))
        .andExpect(jsonPath("$.errors.nationalId").exists())
        .andExpect(jsonPath("$.errors.bloodType").exists())
        .andExpect(jsonPath("$.errors.phone").exists());
  }

  // ---------- Auxiliares ----------

  private RequestPostProcessor login(String username) throws Exception {
    return scenario.login(username);
  }

  private String user(String name, UUID hospital, Role role) {
    return scenario.user(name, hospital, role);
  }

  private String create(RequestPostProcessor as, String body) throws Exception {
    var result =
        mockMvc
            .perform(json(post("/patients"), body).with(as))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private static String patient(String name, String birthDate, String nationalId) {
    return """
        {"fullName":"%s","birthDate":"%s","gender":"FEMALE","nationalId":%s,
         "phone":null,"version":null}"""
        .formatted(name, birthDate, nationalId == null ? "null" : "\"" + nationalId + "\"");
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder builder, String content) {
    return builder.contentType(MediaType.APPLICATION_JSON).content(content);
  }
}
