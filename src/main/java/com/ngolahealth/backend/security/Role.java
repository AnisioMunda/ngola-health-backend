package com.ngolahealth.backend.security;

/**
 * Papéis da plataforma. {@code SUPER_ADMIN} gere a plataforma (hospitais); os restantes trabalham
 * dentro de um hospital. Nunca usar o nome do papel como texto solto: usar este enum ou {@link
 * Authorities}.
 */
public enum Role {
  SUPER_ADMIN,
  ADMIN,
  MANAGER,
  DOCTOR,
  NURSE,
  RECEPTIONIST,
  PHARMACIST,
  FINANCIAL,
  LAB_TECHNICIAN,
  PATIENT;

  /** Autoridade Spring Security correspondente (prefixo {@code ROLE_}). */
  public String authority() {
    return "ROLE_" + name();
  }
}
