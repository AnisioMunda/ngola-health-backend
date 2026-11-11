package com.ngolahealth.backend.security;

/**
 * Expressões reutilizáveis para {@code @PreAuthorize}. Centralizam as regras de acesso e evitam
 * nomes de papéis espalhados pelo código.
 */
public final class Authorities {

  public static final String AUTHENTICATED = "isAuthenticated()";
  public static final String SUPER_ADMIN = "hasRole('SUPER_ADMIN')";

  /** Gerem utilizadores (o ADMIN só no seu hospital). */
  public static final String USER_MANAGERS = "hasAnyRole('SUPER_ADMIN', 'ADMIN')";

  /** Consultam pacientes do próprio hospital (o SUPER_ADMIN não acede a dados clínicos). */
  public static final String PATIENT_READERS =
      "hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE', 'RECEPTIONIST')";

  /** Registam e editam pacientes. */
  public static final String PATIENT_WRITERS =
      "hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST')";

  /** Activam e inactivam pacientes. */
  public static final String PATIENT_ADMINS = "hasRole('ADMIN')";

  /** Consultam a auditoria (o ADMIN e o MANAGER só do próprio hospital). */
  public static final String AUDIT_READERS = "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')";

  /** Consultam utilizadores (o ADMIN e o MANAGER só no seu hospital). */
  public static final String USER_READERS = "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')";

  private Authorities() {}
}
