package com.ngolahealth.backend.modules.inpatient.domain;

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

@Entity
@Table(name = "wards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ward extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(nullable = false, length = 20)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private WardType type;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "gender_policy", nullable = false, length = 10)
  private GenderPolicy genderPolicy;

  @Column(length = 20)
  private String floor;

  @Column(length = 500)
  private String notes;

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

  public Ward(WardDetails d) {
    apply(d);
  }

  public void apply(WardDetails d) {
    this.code = d.code();
    this.name = d.name();
    this.type = d.type();
    this.genderPolicy = d.genderPolicy();
    this.floor = d.floor();
    this.notes = d.notes();
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
