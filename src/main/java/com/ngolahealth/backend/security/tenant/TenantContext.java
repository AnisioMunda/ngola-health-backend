package com.ngolahealth.backend.security.tenant;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Hospital explícito para trabalho fora de um pedido de utilizador (ex.: tarefas agendadas, dados
 * de demonstração). Em pedidos normais o hospital vem do utilizador autenticado.
 */
public final class TenantContext {

  private static final ThreadLocal<UUID> OVERRIDE = new ThreadLocal<>();

  private TenantContext() {}

  /** Executa {@code work} no âmbito do hospital indicado. */
  public static <T> T callAs(UUID hospitalId, Supplier<T> work) {
    UUID previous = OVERRIDE.get();
    OVERRIDE.set(hospitalId);
    try {
      return work.get();
    } finally {
      if (previous == null) {
        OVERRIDE.remove();
      } else {
        OVERRIDE.set(previous);
      }
    }
  }

  static UUID override() {
    return OVERRIDE.get();
  }
}
