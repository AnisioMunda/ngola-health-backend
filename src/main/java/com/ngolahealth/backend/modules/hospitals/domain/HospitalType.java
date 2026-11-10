package com.ngolahealth.backend.modules.hospitals.domain;

/** Tipo de instituição de saúde. Os valores são validados também por CHECK na base de dados. */
public enum HospitalType {
  HOSPITAL,
  CLINIC,
  POLYCLINIC,
  HEALTH_CENTER,
  LABORATORY
}
