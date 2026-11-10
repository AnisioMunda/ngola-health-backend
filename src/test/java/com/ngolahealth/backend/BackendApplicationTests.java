package com.ngolahealth.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Teste de integração da fundação: arranca o contexto completo contra a base MySQL de testes
 * (aplicando o Liquibase) e valida segurança, CORS e health check.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendApplicationTests {

  private static final String FRONTEND_ORIGIN = "http://localhost:4200";

  @Autowired private MockMvc mockMvc;

  @Test
  void healthEndpointIsPublicAndUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void protectedEndpointWithoutTokenReturnsProblemJson401() throws Exception {
    mockMvc
        .perform(get("/qualquer-recurso"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void corsPreflightAllowsFrontendOrigin() throws Exception {
    mockMvc
        .perform(
            options("/qualquer-recurso")
                .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN));
  }

  @Test
  void corsPreflightRejectsUnknownOrigin() throws Exception {
    mockMvc
        .perform(
            options("/qualquer-recurso")
                .header(HttpHeaders.ORIGIN, "http://malicioso.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden());
  }
}
