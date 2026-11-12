package com.ngolahealth.backend.support;

import com.ngolahealth.backend.security.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Dados reais (com commit) para testes de módulos isolados por hospital, que não podem correr numa
 * transacção de teste (o hospital do Hibernate fixa-se ao abrir a sessão). Nomes com sufixo
 * aleatório; {@link #cleanUp()} apaga tudo o que foi criado, por ordem de dependências.
 */
public class TenantScenario {

  private final AuthFixtures fixtures;
  private final MockMvc mockMvc;
  private final JdbcTemplate jdbc;
  private final String suffix = UUID.randomUUID().toString().substring(0, 8);
  private final List<UUID> hospitals = new ArrayList<>();
  private final List<UUID> users = new ArrayList<>();

  public TenantScenario(AuthFixtures fixtures, MockMvc mockMvc, JdbcTemplate jdbc) {
    this.fixtures = fixtures;
    this.mockMvc = mockMvc;
    this.jdbc = jdbc;
  }

  public UUID hospital(String prefix) {
    UUID id = fixtures.hospital(prefix + "-" + suffix.substring(0, 6).toUpperCase());
    hospitals.add(id);
    return id;
  }

  /** Cria o utilizador e devolve o nome de utilizador (com sufixo). */
  public String user(String name, UUID hospital, Role... roles) {
    String username = name + "." + suffix;
    users.add(fixtures.user(username, hospital, roles).getId());
    return username;
  }

  public UUID userId(String username) {
    return UUID.fromString(
        jdbc.queryForObject("SELECT id FROM users WHERE username = ?", String.class, username));
  }

  public RequestPostProcessor login(String username) throws Exception {
    return fixtures.bearer(mockMvc, username);
  }

  /** Cria um utilizador e inicia sessão com ele. */
  public RequestPostProcessor as(String name, UUID hospital, Role... roles) throws Exception {
    return login(user(name, hospital, roles));
  }

  public void cleanUp() {
    for (UUID h : hospitals) {
      String id = h.toString();
      jdbc.update("DELETE FROM audit_events WHERE hospital_id = ?", id);
      jdbc.update("DELETE FROM triage_priority_changes WHERE hospital_id = ?", id);
      jdbc.update("DELETE FROM triage_records WHERE hospital_id = ?", id);
      jdbc.update("DELETE FROM triage_queue_sequences WHERE hospital_id = ?", id);
      jdbc.update("DELETE FROM episodes WHERE hospital_id = ?", id);
      jdbc.update("DELETE FROM patients WHERE hospital_id = ?", id);
      jdbc.update("DELETE FROM patient_record_sequences WHERE hospital_id = ?", id);
    }
    for (UUID u : users) {
      String id = u.toString();
      jdbc.update("DELETE FROM audit_events WHERE actor_id = ?", id);
      jdbc.update("DELETE FROM user_sessions WHERE user_id = ?", id);
      jdbc.update("DELETE FROM user_roles WHERE user_id = ?", id);
      jdbc.update("DELETE FROM users WHERE id = ?", id);
    }
    for (UUID h : hospitals) {
      jdbc.update("DELETE FROM hospitals WHERE id = ?", h.toString());
    }
  }
}
