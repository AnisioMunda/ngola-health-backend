package com.ngolahealth.backend.security;

import java.util.Set;
import java.util.UUID;

/**
 * Utilizador autenticado no pedido actual, carregado da base de dados a cada pedido (o estado e os
 * papéis reflectem alterações imediatamente).
 *
 * @param hospitalId hospital do utilizador; null para SUPER_ADMIN
 * @param sessionId sessão de login a que o token pertence
 */
public record CurrentUser(
    UUID id,
    String username,
    String fullName,
    UUID hospitalId,
    Set<Role> roles,
    UUID sessionId,
    boolean mustChangePassword) {

  public boolean hasRole(Role role) {
    return roles.contains(role);
  }

  public boolean isSuperAdmin() {
    return hasRole(Role.SUPER_ADMIN);
  }
}
