package com.ngolahealth.backend.modules.financial.domain;

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

/** Preço de um acto. Alterar o preço não altera documentos já feitos (os itens copiam-no). */
@Entity
@Table(name = "service_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServicePrice extends HospitalScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  @Column(nullable = false, length = 30)
  private String code;

  @Column(nullable = false, length = 300)
  private String description;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private ServiceCategory category;

  @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal vatRate;

  @Column(name = "vat_exemption_reason", length = 200)
  private String vatExemptionReason;

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

  public ServicePrice(ServicePriceDetails d) {
    apply(d);
  }

  public void apply(ServicePriceDetails d) {
    this.code = d.code();
    this.description = d.description();
    this.category = d.category();
    this.unitPrice = Money.round(d.unitPrice());
    this.vatRate = d.vatRate();
    this.vatExemptionReason = d.vatExemptionReason();
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
