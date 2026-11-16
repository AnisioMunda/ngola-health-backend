package com.ngolahealth.backend.modules.inpatient.domain;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.persistence.HospitalScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Cama de uma enfermaria. A ocupação só muda pelo ciclo do internamento ({@link #occupy}, {@link
 * #release}); fora dele, só os estados de limpeza e manutenção.
 */
@Entity
@Table(name = "beds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bed extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "ward_id", nullable = false, updatable = false, length = 36)
  private UUID wardId;

  @Column(nullable = false, length = 10)
  private String code;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 15)
  private BedType type;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 15)
  private BedStatus status = BedStatus.AVAILABLE;

  @Column(name = "status_note", length = 200)
  private String statusNote;

  @Column(nullable = false)
  private boolean active = true;

  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Bed(UUID wardId, String code, BedType type) {
    this.wardId = wardId;
    this.code = code;
    this.type = type;
  }

  public void edit(String code, BedType type) {
    this.code = code;
    this.type = type;
  }

  public boolean isOccupied() {
    return status == BedStatus.OCCUPIED;
  }

  public void occupy() {
    if (!active || status != BedStatus.AVAILABLE) {
      throw new ConflictException(
          "BED_NOT_AVAILABLE",
          "A cama " + code + " não está livre (" + (active ? statusLabel() : "desactivada") + ").");
    }
    this.status = BedStatus.OCCUPIED;
    this.statusNote = null;
  }

  /** O paciente saiu: a cama fica a aguardar limpeza. */
  public void release() {
    if (status != BedStatus.OCCUPIED) {
      throw new IllegalStateException("Cama " + code + " não estava ocupada");
    }
    this.status = BedStatus.CLEANING;
  }

  /** Limpeza e manutenção, fora do internamento. */
  public void setHousekeeping(BedStatus target, String note) {
    if (target == BedStatus.OCCUPIED) {
      throw new BadRequestException(
          "OCCUPIED_VIA_ADMISSION", "Uma cama só fica ocupada ao internar um paciente.");
    }
    if (status == BedStatus.OCCUPIED) {
      throw new ConflictException(
          "BED_OCCUPIED",
          "A cama " + code + " está ocupada: liberta-se com a alta ou transferência.");
    }
    this.status = target;
    this.statusNote = note;
  }

  public void deactivate() {
    if (status == BedStatus.OCCUPIED) {
      throw new ConflictException("BED_OCCUPIED", "Não se desactiva uma cama ocupada.");
    }
    this.active = false;
  }

  public void activate() {
    this.active = true;
  }

  private String statusLabel() {
    return switch (status) {
      case AVAILABLE -> "livre";
      case OCCUPIED -> "ocupada";
      case CLEANING -> "em limpeza";
      case MAINTENANCE -> "em manutenção";
    };
  }
}
