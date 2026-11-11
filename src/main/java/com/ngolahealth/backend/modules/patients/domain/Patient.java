package com.ngolahealth.backend.modules.patients.domain;

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
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Paciente de um hospital. Nunca é apagado (registo clínico): inactiva-se. */
@Entity
@Table(name = "patients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Patient extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(name = "record_number", nullable = false, updatable = false, length = 20)
  private String recordNumber;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(name = "birth_date", nullable = false)
  private LocalDate birthDate;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 10)
  private Gender gender;

  @Column(name = "national_id", length = 20)
  private String nationalId;

  @Column(name = "health_card_number", length = 30)
  private String healthCardNumber;

  @Column(length = 20)
  private String phone;

  @Column(length = 200)
  private String email;

  @Column(length = 500)
  private String address;

  @Column(length = 100)
  private String province;

  @Column(length = 100)
  private String municipality;

  @Column(name = "emergency_contact_name", length = 200)
  private String emergencyContactName;

  @Column(name = "emergency_contact_phone", length = 20)
  private String emergencyContactPhone;

  @Column(name = "emergency_contact_relationship", length = 50)
  private String emergencyContactRelationship;

  @Column(name = "blood_type", length = 3)
  private String bloodType;

  @Column(columnDefinition = "text")
  private String allergies;

  @Column(name = "chronic_conditions", columnDefinition = "text")
  private String chronicConditions;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_by", length = 36, updatable = false)
  private UUID createdBy;

  @Version
  @Column(nullable = false)
  private long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Patient(String recordNumber, PatientDetails details, UUID createdBy) {
    this.recordNumber = recordNumber;
    this.createdBy = createdBy;
    apply(details);
  }

  public void apply(PatientDetails d) {
    this.fullName = d.fullName();
    this.birthDate = d.birthDate();
    this.gender = d.gender();
    this.nationalId = d.nationalId();
    this.healthCardNumber = d.healthCardNumber();
    this.phone = d.phone();
    this.email = d.email();
    this.address = d.address();
    this.province = d.province();
    this.municipality = d.municipality();
    this.emergencyContactName = d.emergencyContactName();
    this.emergencyContactPhone = d.emergencyContactPhone();
    this.emergencyContactRelationship = d.emergencyContactRelationship();
    this.bloodType = d.bloodType();
    this.allergies = d.allergies();
    this.chronicConditions = d.chronicConditions();
    this.notes = d.notes();
  }

  public int ageOn(LocalDate date) {
    return Period.between(birthDate, date).getYears();
  }

  public void activate() {
    this.active = true;
  }

  public void deactivate() {
    this.active = false;
  }
}
