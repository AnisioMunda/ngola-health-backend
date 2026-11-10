package com.ngolahealth.backend.security;

import com.ngolahealth.backend.common.exception.UnauthorizedException;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Acesso ao utilizador autenticado, para serviços que aplicam regras por hospital. */
@Component
public class CurrentUserProvider {

  public Optional<CurrentUser> find() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof CurrentUser user
        ? Optional.of(user)
        : Optional.empty();
  }

  public CurrentUser get() {
    return find()
        .orElseThrow(
            () ->
                new UnauthorizedException(
                    "UNAUTHENTICATED", "É necessário iniciar sessão para aceder a este recurso."));
  }
}
