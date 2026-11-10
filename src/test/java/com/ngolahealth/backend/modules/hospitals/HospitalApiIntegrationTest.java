package com.ngolahealth.backend.modules.hospitals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ngolahealth.backend.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * API de hospitais ponta a ponta: MySQL real (base de testes, migrações Liquibase aplicadas),
 * segurança, validação e formato de erro. Cada teste é revertido no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HospitalApiIntegrationTest {

  private static final String DEFAULT_HOSPITAL_ID = "00000000-0000-0000-0000-000000000100";

  @Autowired private MockMvc mockMvc;

  // ---------- Segurança ----------

  @Test
  void anonymousCannotReadHospitals() throws Exception {
    mockMvc.perform(get("/hospitals")).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = "SUPER_ADMIN", mode = EnumSource.Mode.EXCLUDE)
  void onlySuperAdminCanManageHospitals(Role role) throws Exception {
    var asRole = as(role);
    mockMvc
        .perform(get("/hospitals/management").with(asRole))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    mockMvc
        .perform(json(post("/hospitals"), body("NEW-001")).with(asRole))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(json(put("/hospitals/" + DEFAULT_HOSPITAL_ID), body("NEW-001")).with(asRole))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(patch("/hospitals/" + DEFAULT_HOSPITAL_ID + "/deactivate").with(asRole))
        .andExpect(status().isForbidden());
  }

  @Test
  void anyAuthenticatedUserCanListActiveHospitalsAndReadOne() throws Exception {
    mockMvc
        .perform(get("/hospitals").with(as(Role.DOCTOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code", hasItem("HCL-001")));
    mockMvc
        .perform(get("/hospitals/" + DEFAULT_HOSPITAL_ID).with(as(Role.NURSE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Hospital Central de Luanda"));
  }

  // ---------- Criação ----------

  @Test
  void createReturnsLocationAndPersists() throws Exception {
    var result =
        mockMvc
            .perform(
                json(
                        post("/hospitals"),
                        """
                        {"name":"  Clínica Sagrada Esperança ","code":" cse-001 ","type":"CLINIC",
                         "province":"Luanda","municipality":"  ","email":"Geral@CSE.AO",
                         "taxId":"5417000000"}
                        """)
                    .with(superAdmin()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Clínica Sagrada Esperança"))
            .andExpect(jsonPath("$.code").value("CSE-001"))
            .andExpect(jsonPath("$.municipality").doesNotExist())
            .andExpect(jsonPath("$.email").value("geral@cse.ao"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.version").value(0))
            .andReturn();

    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    var location = result.getResponse().getHeader("Location");
    assertThat(location).endsWith("/hospitals/" + id);

    mockMvc
        .perform(get("/hospitals/" + id).with(as(Role.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("CLINIC"));
  }

  @Test
  void duplicateCodeIsConflictCaseInsensitive() throws Exception {
    mockMvc
        .perform(json(post("/hospitals"), body("hcl-001")).with(superAdmin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("HOSPITAL_CODE_IN_USE"))
        .andExpect(jsonPath("$.detail").value("Já existe um hospital com o código HCL-001."));
  }

  @Test
  void invalidPayloadReturnsFieldErrorsInPortuguese() throws Exception {
    mockMvc
        .perform(
            json(
                    post("/hospitals"),
                    """
                    {"name":" ","code":"x","province":"","email":"nao-e-email",
                     "phone":"abc","taxId":"12"}
                    """)
                .with(superAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.name").value("O nome é obrigatório."))
        .andExpect(
            jsonPath("$.errors.code").value("O código deve ter 3 a 20 letras, números ou hífenes."))
        .andExpect(jsonPath("$.errors.type").value("O tipo de instituição é obrigatório."))
        .andExpect(jsonPath("$.errors.province").value("A província é obrigatória."))
        .andExpect(jsonPath("$.errors.email").value("Indique um correio electrónico válido."))
        .andExpect(jsonPath("$.errors.phone").exists())
        .andExpect(jsonPath("$.errors.taxId").value("O NIF deve ter 9 a 14 letras ou números."));
  }

  @Test
  void unknownTypeIsMalformedRequest() throws Exception {
    mockMvc
        .perform(
            json(post("/hospitals"), body("NEW-001").replace("HOSPITAL", "CASTELO"))
                .with(superAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  // ---------- Edição ----------

  @Test
  void updateChangesDataAndIncrementsVersion() throws Exception {
    mockMvc
        .perform(
            json(put("/hospitals/" + DEFAULT_HOSPITAL_ID), body("HCL-001", "Hospital Novo", 0L))
                .with(superAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Hospital Novo"))
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void updateWithStaleVersionIsConflict() throws Exception {
    mockMvc
        .perform(
            json(put("/hospitals/" + DEFAULT_HOSPITAL_ID), body("HCL-001", "Outro", 42L))
                .with(superAdmin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
  }

  @Test
  void updateToCodeOfAnotherHospitalIsConflict() throws Exception {
    mockMvc
        .perform(json(post("/hospitals"), body("OUT-001")).with(superAdmin()))
        .andExpect(status().isCreated());

    mockMvc
        .perform(json(put("/hospitals/" + DEFAULT_HOSPITAL_ID), body("out-001")).with(superAdmin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("HOSPITAL_CODE_IN_USE"));
  }

  @Test
  void unknownHospitalIs404AndBadIdIs400() throws Exception {
    mockMvc
        .perform(get("/hospitals/11111111-1111-1111-1111-111111111111").with(as(Role.ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HOSPITAL_NOT_FOUND"));
    mockMvc
        .perform(get("/hospitals/nao-e-uuid").with(as(Role.ADMIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  // ---------- Estado e pesquisa ----------

  @Test
  void deactivatedHospitalLeavesActiveListButStaysInManagement() throws Exception {
    mockMvc
        .perform(patch("/hospitals/" + DEFAULT_HOSPITAL_ID + "/deactivate").with(superAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(get("/hospitals").with(as(Role.DOCTOR)))
        .andExpect(jsonPath("$[*].code", not(hasItem("HCL-001"))));
    mockMvc
        .perform(get("/hospitals/management").param("active", "false").with(superAdmin()))
        .andExpect(jsonPath("$.content[*].code", hasItem("HCL-001")));

    mockMvc
        .perform(patch("/hospitals/" + DEFAULT_HOSPITAL_ID + "/activate").with(superAdmin()))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void managementSearchIsPaginatedAndFiltersByText() throws Exception {
    mockMvc
        .perform(
            json(post("/hospitals"), body("HBG-001", "Hospital do Bengo", null)).with(superAdmin()))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/hospitals/management").param("search", "beng").with(superAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].code").value("HBG-001"));

    mockMvc
        .perform(get("/hospitals/management").param("size", "1").with(superAdmin()))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.content.length()").value(1));

    mockMvc
        .perform(get("/hospitals/management").param("search", "%").with(superAdmin()))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void pageSizeOutOfRangeIsValidationError() throws Exception {
    mockMvc
        .perform(get("/hospitals/management").param("size", "500").with(superAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.size").value("O tamanho da página não pode exceder 100."));
  }

  // ---------- Auxiliares ----------

  private static RequestPostProcessor as(Role role) {
    return user("utilizador-" + role.name().toLowerCase()).roles(role.name());
  }

  private static RequestPostProcessor superAdmin() {
    return as(Role.SUPER_ADMIN);
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder builder, String content) {
    return builder.contentType(MediaType.APPLICATION_JSON).content(content);
  }

  private static String body(String code) {
    return body(code, "Hospital de Teste", null);
  }

  private static String body(String code, String name, Long version) {
    return """
        {"name":"%s","code":"%s","type":"HOSPITAL","province":"Luanda","version":%s}
        """
        .formatted(name, code, version);
  }
}
