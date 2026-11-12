package com.ngolahealth.backend.modules.laboratory.domain;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Exame do catálogo do laboratório. Nunca é apagado: desactiva-se. */
@Entity
@Table(name = "lab_tests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabTest extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(nullable = false, length = 30)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private TestCategory category;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "sample_type", nullable = false, length = 20)
  private SampleType sampleType;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "result_type", nullable = false, length = 15)
  private ResultType resultType;

  @Column(length = 30)
  private String unit;

  @Column(name = "reference_low", precision = 12, scale = 4)
  private BigDecimal referenceLow;

  @Column(name = "reference_high", precision = 12, scale = 4)
  private BigDecimal referenceHigh;

  @Column(name = "reference_text", length = 200)
  private String referenceText;

  @Column(name = "turnaround_hours", nullable = false)
  private int turnaroundHours;

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

  public LabTest(LabTestDetails d) {
    apply(d);
  }

  public void apply(LabTestDetails d) {
    this.code = d.code();
    this.name = d.name();
    this.category = d.category();
    this.sampleType = d.sampleType();
    this.resultType = d.resultType();
    this.unit = d.unit();
    this.referenceLow = d.referenceLow();
    this.referenceHigh = d.referenceHigh();
    this.referenceText = d.referenceText();
    this.turnaroundHours = d.turnaroundHours();
  }

  public void activate() {
    this.active = true;
  }

  public void deactivate() {
    this.active = false;
  }
}
