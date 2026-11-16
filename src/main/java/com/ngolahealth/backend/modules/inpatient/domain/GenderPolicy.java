package com.ngolahealth.backend.modules.inpatient.domain;

import com.ngolahealth.backend.modules.patients.domain.Gender;

/** Enfermaria mista ou só masculina/feminina. */
public enum GenderPolicy {
  MIXED,
  MALE,
  FEMALE;

  public boolean accepts(Gender gender) {
    return this == MIXED
        || (this == MALE && gender == Gender.MALE)
        || (this == FEMALE && gender == Gender.FEMALE);
  }
}
