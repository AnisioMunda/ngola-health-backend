package com.ngolahealth.backend.modules.inpatient.domain;

/** ACTIVE → DISCHARGED (alta, incluindo óbito) ou CANCELLED (registo por engano). */
public enum AdmissionStatus {
  ACTIVE,
  DISCHARGED,
  CANCELLED
}
