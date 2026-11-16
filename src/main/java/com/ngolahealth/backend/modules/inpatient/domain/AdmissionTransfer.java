package com.ngolahealth.backend.modules.inpatient.domain;

import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
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

/** Mudança de cama (na mesma enfermaria ou para outra). Imutável. */
@Entity
@Table(name = "admission_transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdmissionTransfer extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "admission_id", nullable = false, updatable = false, length = 36)
  private UUID admissionId;

  @Column(name = "from_ward_id", nullable = false, updatable = false, length = 36)
  private UUID fromWardId;

  @Column(name = "from_bed_id", nullable = false, updatable = false, length = 36)
  private UUID fromBedId;

  @Column(name = "to_ward_id", nullable = false, updatable = false, length = 36)
  private UUID toWardId;

  @Column(name = "to_bed_id", nullable = false, updatable = false, length = 36)
  private UUID toBedId;

  @Column(nullable = false, updatable = false, length = 300)
  private String reason;

  @Column(name = "transferred_by", nullable = false, updatable = false, length = 36)
  private UUID transferredBy;

  @Column(name = "transferred_at", nullable = false, updatable = false)
  private Instant transferredAt;

  public AdmissionTransfer(UUID admissionId, Bed from, Bed to, String reason, UUID by, Instant at) {
    this.admissionId = admissionId;
    this.fromWardId = from.getWardId();
    this.fromBedId = from.getId();
    this.toWardId = to.getWardId();
    this.toBedId = to.getId();
    this.reason = reason;
    this.transferredBy = by;
    this.transferredAt = at;
  }
}
