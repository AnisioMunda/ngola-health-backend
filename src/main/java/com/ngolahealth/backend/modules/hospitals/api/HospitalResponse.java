package com.ngolahealth.backend.modules.hospitals.api;

import com.ngolahealth.backend.modules.hospitals.domain.Hospital;
import com.ngolahealth.backend.modules.hospitals.domain.HospitalType;
import java.time.Instant;
import java.util.UUID;

public record HospitalResponse(
    UUID id,
    String name,
    String code,
    HospitalType type,
    String province,
    String municipality,
    String address,
    String phone,
    String email,
    String taxId,
    boolean active,
    long version,
    Instant createdAt,
    Instant updatedAt) {

  public static HospitalResponse from(Hospital h) {
    return new HospitalResponse(
        h.getId(),
        h.getName(),
        h.getCode(),
        h.getType(),
        h.getProvince(),
        h.getMunicipality(),
        h.getAddress(),
        h.getPhone(),
        h.getEmail(),
        h.getTaxId(),
        h.isActive(),
        h.getVersion(),
        h.getCreatedAt(),
        h.getUpdatedAt());
  }
}
