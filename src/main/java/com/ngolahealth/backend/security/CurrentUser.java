package com.ngolahealth.backend.security;

import java.util.Set;
import java.util.UUID;

/**
 * Utilizador autenticado no pedido actual, carregado da base de dados a cada pedido (o estado e os
 * papéis reflectem alterações imediatamente).
 *
 * @param hospitalId hospital do utilizador; null para SUPER_ADMIN
 * @param sessionId sessão de login a que o token pertence
 * @param patientId ficha do doente, nas contas do portal; null para o pessoal
 */
public record CurrentUser(
    UUID id,
    String username,
    String fullName,
    UUID hospitalId,
    Set<Role> roles,
    UUID sessionId,
    boolean mustChangePassword,
    UUID patientId) {

  /** Conta do portal do paciente (token com a audiência do portal). */
  public boolean isPortal() {
    return patientId != null;
  }

  public boolean hasRole(Role role) {
    return roles.contains(role);
  }

  public boolean isSuperAdmin() {
    return hasRole(Role.SUPER_ADMIN);
  }
}
