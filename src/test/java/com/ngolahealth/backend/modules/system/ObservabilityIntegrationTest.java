package com.ngolahealth.backend.modules.system;

import static com.ngolahealth.backend.support.AuthFixtures.DEFAULT_HOSPITAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngolahealth.backend.modules.users.domain.UserSessionRepository;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.support.AuthFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Logs de acesso, métricas protegidas e estado do sistema. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIntegrationTest {

  private static final String SCRAPER = "prometheus-test";
  private static final String SCRAPER_PASSWORD = "senha-metricas-teste-2026";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private UserSessionRepository sessions;

  private RequestPostProcessor root;
  private RequestPostProcessor admin;

  @BeforeEach
  void setUp() throws Exception {
    fixtures.user("root.obs", null, Role.SUPER_ADMIN);
    fixtures.user("admin.obs", DEFAULT_HOSPITAL, Role.ADMIN);
    root = fixtures.bearer(mockMvc, "root.obs");
    admin = fixtures.bearer(mockMvc, "admin.obs");
  }

  // ---------- Endpoints de gestão ----------

  @Test
  void healthAndProbesArePublicButDetailsAreNot() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mockMvc
        .perform(get("/actuator/health").with(root))
        .andExpect(jsonPath("$.components.db.status").value("UP"));
  }

  @Test
  void infoAndMetricsOnlyForPlatformAdmin() throws Exception {
    mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator/metrics").with(admin)).andExpect(status().isForbidden());
    mockMvc
        .perform(get("/actuator/metrics").with(root))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.names", hasItem("http.server.requests")))
        .andExpect(jsonPath("$.names", hasItem("hikaricp.connections.active")));
    mockMvc
        .perform(get("/actuator/info").with(root))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.java.version", notNullValue()));
  }

  @Test
  void prometheusNeedsScraperCredentialsNotSessionTokens() throws Exception {
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/actuator/prometheus").with(httpBasic(SCRAPER, "senha-errada-123456789")))
        .andExpect(status().isUnauthorized());
    // Nem a administração da plataforma entra com o token de sessão.
    mockMvc.perform(get("/actuator/prometheus").with(root)).andExpect(status().isUnauthorized());

    // Uma falha de login para o contador aparecer.
    fixtures.login(mockMvc, "admin.obs", "senha-errada");
    mockMvc
        .perform(get("/actuator/prometheus").with(httpBasic(SCRAPER, SCRAPER_PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("http_server_requests_seconds")))
        .andExpect(
            content()
                .string(
                    containsString(
                        "ngolahealth_auth_logins_total{application=\"ngolahealth-backend\",outcome=\"invalid_credentials\"}")))
        .andExpect(content().string(containsString("outcome=\"success\"")));
  }

  // ---------- Logs ----------

  @Test
  void accessLogHasOneLinePerRequestWithoutQueryString(CapturedOutput output) throws Exception {
    mockMvc
        .perform(get("/patients").param("q", "Maria Segredo").with(admin))
        .andExpect(status().isOk());
    var line = output.getOut().lines().filter(l -> l.contains("GET /patients 200")).findFirst();
    assertThat(line).isPresent();
    assertThat(line.get()).contains("ngolahealth.http").matches(".*\\d+ms$");
    assertThat(output.getOut()).doesNotContain("Maria Segredo");
    // As sondas não enchem o log.
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    assertThat(output.getOut()).doesNotContain("GET /actuator/health");
  }

  // ---------- Contagem truncada ----------

  @Test
  void cappedCountRunsOnMysqlAndKeepsExactTotalsBelowTheCap() throws Exception {
    // Página de 1 com vários eventos (os dois logins do setUp, vistos pela plataforma): obriga a
    // correr a contagem truncada.
    mockMvc
        .perform(get("/audit").param("size", "1").with(root))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)))
        .andExpect(jsonPath("$.totalCapped").value(false));
    for (var path : new String[] {"/patients", "/episodes", "/lab/requests", "/prescriptions"}) {
      mockMvc
          .perform(get(path).param("size", "1").param("page", "1").with(admin))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalCapped").value(false));
    }
  }

  // ---------- Estado do sistema ----------

  @Test
  void systemStatusOnlyForPlatformAdmin() throws Exception {
    mockMvc.perform(get("/system/status").with(admin)).andExpect(status().isForbidden());
    // O estado conta as sessões por SQL: grava antes as que o teste criou (fora do teste já estão).
    sessions.flush();
    mockMvc
        .perform(get("/system/status").with(root))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.database.up").value(true))
        .andExpect(jsonPath("$.database.migrations", greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.database.lastMigration", notNullValue()))
        .andExpect(jsonPath("$.application.uptimeSeconds", greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.jvm.heapUsedBytes", greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.logins.success", greaterThanOrEqualTo(2)))
        .andExpect(jsonPath("$.activeSessions", greaterThanOrEqualTo(2)))
        .andExpect(jsonPath("$.jobs[*].name", hasItem("NotificationCleanupJob")))
        .andExpect(jsonPath("$.jobs[*].label", hasItem("Limpeza de notificações antigas")));
  }
}
