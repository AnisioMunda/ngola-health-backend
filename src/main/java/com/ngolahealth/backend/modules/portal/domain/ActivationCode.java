package com.ngolahealth.backend.modules.portal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Código de activação do portal. Não é filtrado por hospital (a activação é anónima e o código é
 * que indica o hospital); o hospital fica guardado explicitamente.
 */
@Entity
@Table(name = "portal_activation_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivationCode {

  /** Tentativas erradas (data de nascimento) antes de o código deixar de servir. */
  public static final int MAX_ATTEMPTS = 5;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "hospital_id", nullable = false, updatable = false, length = 36)
  private UUID hospitalId;

  @Column(name = "patient_id", nullable = false, updatable = false, length = 36)
  private UUID patientId;

  @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
  private String codeHash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_by", nullable = false, updatable = false, length = 36)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public ActivationCode(
      UUID hospitalId,
      UUID patientId,
      String codeHash,
      Instant expiresAt,
      UUID createdBy,
      Instant createdAt) {
    this.hospitalId = hospitalId;
    this.patientId = patientId;
    this.codeHash = codeHash;
    this.expiresAt = expiresAt;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public boolean isUsable(Instant now) {
    return usedAt == null
        && revokedAt == null
        && now.isBefore(expiresAt)
        && failedAttempts < MAX_ATTEMPTS;
  }

  public void use(Instant at) {
    this.usedAt = at;
  }

  public void revoke(Instant at) {
    if (usedAt == null && revokedAt == null) {
      this.revokedAt = at;
    }
  }
}
