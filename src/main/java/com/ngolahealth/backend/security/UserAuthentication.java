package com.ngolahealth.backend.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Autenticação de um {@link CurrentUser}; as autoridades são os papéis (ROLE_*). */
public class UserAuthentication extends AbstractAuthenticationToken {

  private final CurrentUser user;

  public UserAuthentication(CurrentUser user) {
    super(user.roles().stream().map(r -> new SimpleGrantedAuthority(r.authority())).toList());
    this.user = user;
    setAuthenticated(true);
  }

  @Override
  public CurrentUser getPrincipal() {
    return user;
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public String getName() {
    return user.username();
  }
}
