package com.ngolahealth.backend.security;

/**
 * Expressões reutilizáveis para {@code @PreAuthorize}. Centralizam as regras de acesso e evitam
 * nomes de papéis espalhados pelo código.
 */
public final class Authorities {

  public static final String AUTHENTICATED = "isAuthenticated()";
  public static final String SUPER_ADMIN = "hasRole('SUPER_ADMIN')";

  private Authorities() {}
}
