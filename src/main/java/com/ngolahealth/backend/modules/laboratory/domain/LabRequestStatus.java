package com.ngolahealth.backend.modules.laboratory.domain;

/** PENDING → COLLECTED → IN_ANALYSIS → COMPLETED (todos validados); CANCELLED antes disso. */
public enum LabRequestStatus {
  PENDING,
  COLLECTED,
  IN_ANALYSIS,
  COMPLETED,
  CANCELLED
}
