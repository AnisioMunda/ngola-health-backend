package com.ngolahealth.backend.dev;

import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.security.UserAuthentication;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Executa um passo do gerador "como" um utilizador: os serviços aplicam as mesmas regras (hospital,
 * autor, auditoria) que num pedido real.
 */
final class DemoRunAs {

  private DemoRunAs() {}

  record Actor(UUID id, String username, String fullName, UUID hospitalId, Role role) {}

  static <T> T as(Actor actor, Supplier<T> work) {
    var previous = SecurityContextHolder.getContext().getAuthentication();
    var user =
        new CurrentUser(
            actor.id(),
            actor.username(),
            actor.fullName(),
            actor.hospitalId(),
            EnumSet.of(actor.role()),
            UUID.randomUUID(),
            false,
            null);
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
    try {
      return work.get();
    } finally {
      SecurityContextHolder.getContext().setAuthentication(previous);
    }
  }

  static void run(Actor actor, Runnable work) {
    as(
        actor,
        () -> {
          work.run();
          return null;
        });
  }
}
