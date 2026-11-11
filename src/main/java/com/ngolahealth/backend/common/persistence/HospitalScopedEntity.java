package com.ngolahealth.backend.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.TenantId;

/**
 * Base das entidades que pertencem a um hospital. O Hibernate preenche {@code hospital_id} ao
 * inserir e filtra todas as consultas pelo hospital do utilizador (R11): nenhum serviço precisa de
 * o fazer à mão, nem se pode esquecer.
 */
@MappedSuperclass
@Getter
public abstract class HospitalScopedEntity {

  @TenantId
  @Column(name = "hospital_id", nullable = false, updatable = false, length = 36)
  private UUID hospitalId;
}
