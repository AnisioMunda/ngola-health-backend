package com.ngolahealth.backend.modules.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Sessão de login. O token de acesso transporta o id da sessão; o refresh token é {@code
 * <id>.<segredo>} e só o hash do segredo é guardado. Cada renovação troca o segredo (rotação).
 */
@Entity
@Table(name = "user_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession {

  /** Janela em que o refresh token anterior ainda é aceite como "corrida" entre separadores. */
  public static final Duration REUSE_GRACE = Duration.ofSeconds(30);

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false, length = 36)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "previous_token_hash", length = 64)
  private String previousTokenHash;

  @Column(name = "rotated_at")
  private Instant rotatedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_reason", length = 30)
  private String revokedReason;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  public UserSession(
      UUID userId,
      String tokenHash,
      Instant now,
      Duration ttl,
      String ipAddress,
      String userAgent) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.createdAt = now;
    this.expiresAt = now.plus(ttl);
    this.ipAddress = ipAddress;
    this.userAgent =
        userAgent == null || userAgent.length() <= 255 ? userAgent : userAgent.substring(0, 255);
  }

  public boolean isActive(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  /** O hash corresponde ao refresh token anterior, apresentado pouco depois de uma rotação. */
  public boolean isRecentlyRotated(String hash, Instant now) {
    return hash.equals(previousTokenHash)
        && rotatedAt != null
        && rotatedAt.plus(REUSE_GRACE).isAfter(now);
  }

  public void rotate(String newHash, Instant now, Duration ttl) {
    this.previousTokenHash = this.tokenHash;
    this.tokenHash = newHash;
    this.rotatedAt = now;
    this.expiresAt = now.plus(ttl);
  }

  public void revoke(Instant now, SessionRevocationReason reason) {
    if (revokedAt == null) {
      this.revokedAt = now;
      this.revokedReason = reason.name();
    }
  }
}
