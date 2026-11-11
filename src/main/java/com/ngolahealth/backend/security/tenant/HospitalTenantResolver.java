package com.ngolahealth.backend.security.tenant;

import com.ngolahealth.backend.security.CurrentUser;
import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Indica ao Hibernate o hospital da sessão: o de {@link TenantContext}, senão o do utilizador
 * autenticado. Sem hospital (SUPER_ADMIN, anónimo, arranque) usa um identificador que não existe:
 * as consultas a entidades por hospital não devolvem nada e as inserções falham (chave
 * estrangeira). Nunca há acesso "a todos os hospitais" por omissão.
 *
 * <p>Atenção: o hospital é fixado quando a sessão Hibernate abre (início da transacção).
 */
@Component
public class HospitalTenantResolver implements CurrentTenantIdentifierResolver<UUID> {

  public static final UUID NO_HOSPITAL = new UUID(0L, 0L);

  @Override
  public UUID resolveCurrentTenantIdentifier() {
    UUID override = TenantContext.override();
    if (override != null) {
      return override;
    }
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.getPrincipal() instanceof CurrentUser user
        && user.hospitalId() != null) {
      return user.hospitalId();
    }
    return NO_HOSPITAL;
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return false;
  }
}
