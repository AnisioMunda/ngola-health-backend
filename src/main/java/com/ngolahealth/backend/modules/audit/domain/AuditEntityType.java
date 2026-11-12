package com.ngolahealth.backend.modules.audit.domain;

/** Tipo de registo afectado. Cada módulo novo acrescenta o seu. */
public enum AuditEntityType {
  HOSPITAL,
  USER,
  PATIENT,
  EPISODE,
  TRIAGE,
  MEDICATION,
  PRESCRIPTION,
  LAB_TEST,
  LAB_REQUEST,
  SYSTEM
}
