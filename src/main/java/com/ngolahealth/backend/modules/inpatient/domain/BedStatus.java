package com.ngolahealth.backend.modules.inpatient.domain;

/** OCCUPIED só pelo internamento; depois de libertada, a cama passa por limpeza. */
public enum BedStatus {
  AVAILABLE,
  OCCUPIED,
  CLEANING,
  MAINTENANCE
}
