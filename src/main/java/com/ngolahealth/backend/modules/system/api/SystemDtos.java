package com.ngolahealth.backend.modules.system.api;

import java.time.Instant;
import java.util.List;

/** Estado da aplicação para a administração da plataforma. Contadores "desde o arranque". */
public final class SystemDtos {

  private SystemDtos() {}

  public record SystemStatus(
      Instant generatedAt,
      Application application,
      Database database,
      Jvm jvm,
      Http http,
      Logins logins,
      long activeSessions,
      List<Job> jobs) {}

  public record Application(
      String version, Instant builtAt, Instant startedAt, long uptimeSeconds, String javaVersion) {}

  /**
   * @param up a base respondeu a {@code SELECT 1}
   * @param latencyMs tempo dessa consulta
   * @param migrations número de changesets Liquibase aplicados
   * @param lastMigration identificador do último changeset aplicado
   */
  public record Database(
      boolean up,
      Long latencyMs,
      Integer poolActive,
      Integer poolIdle,
      Integer poolMax,
      Integer poolPending,
      long migrations,
      String lastMigration,
      Instant lastMigrationAt) {}

  public record Jvm(
      long heapUsedBytes,
      long heapMaxBytes,
      Double processCpu,
      int threads,
      long diskFreeBytes,
      long diskTotalBytes) {}

  /** Pedidos à API (sem {@code /actuator}). */
  public record Http(
      long requests, long clientErrors, long serverErrors, Double meanMs, Double maxMs) {}

  public record Logins(long success, long failed, long locked) {}

  /**
   * Tarefa agendada.
   *
   * @param lastStatus {@code STARTED}, {@code SUCCESS}, {@code ERROR} ou {@code null} (ainda não
   *     correu desde o arranque)
   */
  public record Job(
      String name,
      String label,
      String cron,
      Instant nextRun,
      String lastStatus,
      Instant lastRunAt,
      String lastError) {}
}
