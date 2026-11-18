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
  SCHEDULE,
  SCHEDULE_BLOCK,
  APPOINTMENT,
  WARD,
  BED,
  ADMISSION,
  /** Módulo financeiro retirado: mantido para ler eventos de auditoria antigos. */
  SERVICE_PRICE,
  /** Módulo financeiro retirado: mantido para ler eventos de auditoria antigos. */
  INVOICE,
  SHIFT,
  LEAVE_REQUEST,
  ATTENDANCE,
  EQUIPMENT,
  SYSTEM
}
