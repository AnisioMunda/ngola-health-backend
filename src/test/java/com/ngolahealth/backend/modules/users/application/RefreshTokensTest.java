package com.ngolahealth.backend.modules.users.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngolahealth.backend.modules.users.domain.UserSession;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokensTest {

  @Test
  void formatAndParseRoundTrip() {
    var id = UUID.randomUUID();
    var secret = RefreshTokens.newSecret();
    var parsed = RefreshTokens.parse(RefreshTokens.format(id, secret)).orElseThrow();
    assertThat(parsed.sessionId()).isEqualTo(id);
    assertThat(parsed.secret()).isEqualTo(secret);
  }

  @Test
  void parseRejectsGarbage() {
    assertThat(RefreshTokens.parse(null)).isEmpty();
    assertThat(RefreshTokens.parse("sem-ponto")).isEmpty();
    assertThat(RefreshTokens.parse("nao-uuid.segredo")).isEmpty();
    assertThat(RefreshTokens.parse(UUID.randomUUID() + ".")).isEmpty();
  }

  @Test
  void secretsAreRandomAndHashIsStable() {
    assertThat(RefreshTokens.newSecret()).isNotEqualTo(RefreshTokens.newSecret());
    assertThat(RefreshTokens.hash("abc")).hasSize(64).isEqualTo(RefreshTokens.hash("abc"));
    assertThat(RefreshTokens.matches(RefreshTokens.hash("a"), RefreshTokens.hash("b"))).isFalse();
  }

  @Test
  void sessionAcceptsPreviousTokenOnlyWithinGraceWindow() {
    var now = Instant.parse("2026-11-10T10:00:00Z");
    var session = new UserSession(UUID.randomUUID(), "h1", now, Duration.ofHours(12), null, null);
    session.rotate("h2", now, Duration.ofHours(12));

    assertThat(session.isRecentlyRotated("h1", now.plusSeconds(10))).isTrue();
    assertThat(session.isRecentlyRotated("h1", now.plusSeconds(31))).isFalse();
    assertThat(session.isRecentlyRotated("outro", now)).isFalse();
  }

  @Test
  void sessionExpiresAndRevokes() {
    var now = Instant.parse("2026-11-10T10:00:00Z");
    var session =
        new UserSession(UUID.randomUUID(), "h", now, Duration.ofHours(1), null, "x".repeat(400));
    assertThat(session.getUserAgent()).hasSize(255);
    assertThat(session.isActive(now.plusSeconds(3599))).isTrue();
    assertThat(session.isActive(now.plusSeconds(3600))).isFalse();
    session.revoke(
        now, com.ngolahealth.backend.modules.users.domain.SessionRevocationReason.LOGOUT);
    assertThat(session.isActive(now)).isFalse();
  }
}
