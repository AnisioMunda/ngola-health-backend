package com.ngolahealth.backend.modules.hospitals.domain;

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
 * Instituição de saúde registada na plataforma. Nunca é apagada: desactiva-se, para preservar o
 * histórico clínico e financeiro associado.
 */
@Entity
@Table(name = "hospitals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hospital {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, unique = true, length = 20)
  private String code;

  // VARCHAR + CHECK em vez do ENUM nativo do MySQL: acrescentar um tipo não exige ALTER TABLE.
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 30)
  private HospitalType type;

  @Column(nullable = false, length = 100)
  private String province;

  @Column(length = 100)
  private String municipality;

  @Column(length = 500)
  private String address;

  @Column(length = 20)
  private String phone;

  @Column(length = 200)
  private String email;

  @Column(name = "tax_id", length = 50)
  private String taxId;

  @Column(nullable = false)
  private boolean active = true;

  @Version
  @Column(nullable = false)
  private long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Hospital(HospitalDetails details) {
    apply(details);
  }

  /** Substitui os dados descritivos do hospital (os valores já chegam normalizados). */
  public void apply(HospitalDetails details) {
    this.name = details.name();
    this.code = details.code();
    this.type = details.type();
    this.province = details.province();
    this.municipality = details.municipality();
    this.address = details.address();
    this.phone = details.phone();
    this.email = details.email();
    this.taxId = details.taxId();
  }

  public void activate() {
    this.active = true;
  }

  public void deactivate() {
    this.active = false;
  }
}
