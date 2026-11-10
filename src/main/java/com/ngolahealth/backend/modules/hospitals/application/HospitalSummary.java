package com.ngolahealth.backend.modules.hospitals.application;

import com.ngolahealth.backend.modules.hospitals.domain.Hospital;
import java.util.UUID;

/** Vista mínima de um hospital, exposta a outros módulos. */
public record HospitalSummary(UUID id, String name, String code, boolean active) {

  static HospitalSummary from(Hospital h) {
    return new HospitalSummary(h.getId(), h.getName(), h.getCode(), h.isActive());
  }
}
