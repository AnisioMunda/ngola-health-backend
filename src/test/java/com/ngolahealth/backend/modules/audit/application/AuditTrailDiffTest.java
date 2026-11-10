package com.ngolahealth.backend.modules.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

/** Snapshot e diferenças com o JsonMapper configurado pelo Spring Boot (sem base de dados). */
@JsonTest
class AuditTrailDiffTest {

  @Autowired private JsonMapper jsonMapper;

  record Sample(
      String name,
      String code,
      String email,
      List<String> roles,
      long version,
      Instant updatedAt) {}

  private AuditTrail trail() {
    return new AuditTrail(null, jsonMapper, Clock.systemUTC());
  }

  @Test
  void diffListsOnlyChangedBusinessFields() {
    var before = new Sample("Hospital A", "HA-1", null, List.of("ADMIN"), 1, Instant.EPOCH);
    var after =
        new Sample("Hospital B", "HA-1", "a@b.ao", List.of("ADMIN", "MANAGER"), 2, Instant.now());

    Map<String, Object> diff = trail().diff(before, after);

    assertThat(diff).containsOnlyKeys("name", "email", "roles");
    assertThat(diff.get("name")).isEqualTo(Map.of("from", "Hospital A", "to", "Hospital B"));
    @SuppressWarnings("unchecked")
    var email = (Map<String, Object>) diff.get("email");
    assertThat(email).containsEntry("from", null).containsEntry("to", "a@b.ao");
  }

  @Test
  void diffOfIdenticalObjectsIsEmpty() {
    var sample = new Sample("A", "B", null, List.of(), 1, Instant.EPOCH);
    assertThat(trail().diff(sample, sample)).isEmpty();
  }

  @Test
  void snapshotOmitsNullsAndTechnicalFields() {
    var sample = new Sample("A", "B", null, List.of("X"), 3, Instant.EPOCH);
    assertThat(trail().snapshot(sample)).containsOnlyKeys("name", "code", "roles");
  }
}
