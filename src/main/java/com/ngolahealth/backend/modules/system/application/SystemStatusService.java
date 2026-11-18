package com.ngolahealth.backend.modules.system.application;

import com.ngolahealth.backend.modules.system.api.SystemDtos.Application;
import com.ngolahealth.backend.modules.system.api.SystemDtos.Database;
import com.ngolahealth.backend.modules.system.api.SystemDtos.Http;
import com.ngolahealth.backend.modules.system.api.SystemDtos.Job;
import com.ngolahealth.backend.modules.system.api.SystemDtos.Jvm;
import com.ngolahealth.backend.modules.system.api.SystemDtos.Logins;
import com.ngolahealth.backend.modules.system.api.SystemDtos.SystemStatus;
import com.ngolahealth.backend.modules.users.application.LoginMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.stereotype.Service;

/**
 * Reúne o estado da aplicação a partir das métricas (Micrometer), da base de dados e das tarefas
 * agendadas. Não lê dados clínicos nem de um hospital em particular.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemStatusService {

  /** Nomes legíveis das tarefas agendadas (classe → descrição). */
  private static final Map<String, String> JOB_LABELS =
      Map.of(
          "StockAlertJob", "Resumo diário de stock da farmácia",
          "AgendaReminderJob", "Lembrete da agenda de amanhã aos médicos",
          "SessionCleanupJob", "Limpeza de sessões expiradas",
          "EquipmentDueJob", "Manutenções e calibrações a vencer",
          "NotificationCleanupJob", "Limpeza de notificações antigas");

  private final MeterRegistry registry;
  private final JdbcTemplate jdbc;
  private final ObjectProvider<BuildProperties> build;
  private final List<ScheduledTaskHolder> scheduledTasks;
  private final Clock clock;

  public SystemStatus status() {
    Instant now = clock.instant();
    return new SystemStatus(
        now, application(), database(), jvm(), http(), logins(), activeSessions(now), jobs());
  }

  private Application application() {
    var runtime = ManagementFactory.getRuntimeMXBean();
    var info = build.getIfAvailable();
    return new Application(
        info == null ? null : info.getVersion(),
        info == null ? null : info.getTime(),
        Instant.ofEpochMilli(runtime.getStartTime()),
        runtime.getUptime() / 1000,
        System.getProperty("java.version"));
  }

  private Database database() {
    boolean up;
    Long latency = null;
    long migrations = 0;
    String lastId = null;
    Instant lastAt = null;
    try {
      long start = System.nanoTime();
      jdbc.queryForObject("SELECT 1", Integer.class);
      latency = (System.nanoTime() - start) / 1_000_000;
      up = true;
      migrations = count("SELECT COUNT(*) FROM DATABASECHANGELOG");
      var last =
          jdbc.queryForList(
              "SELECT ID, DATEEXECUTED FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED DESC LIMIT 1");
      if (!last.isEmpty()) {
        lastId = (String) last.getFirst().get("ID");
        lastAt = toInstant(last.getFirst().get("DATEEXECUTED"));
      }
    } catch (RuntimeException e) {
      log.warn("Estado do sistema: base de dados indisponível: {}", e.getMessage());
      up = false;
    }
    return new Database(
        up,
        latency,
        gaugeInt("hikaricp.connections.active"),
        gaugeInt("hikaricp.connections.idle"),
        gaugeInt("hikaricp.connections.max"),
        gaugeInt("hikaricp.connections.pending"),
        migrations,
        lastId,
        lastAt);
  }

  private Jvm jvm() {
    var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    var disk = new File(".").getAbsoluteFile();
    Double cpu = gauge("process.cpu.usage");
    return new Jvm(
        heap.getUsed(),
        heap.getMax(),
        cpu == null || cpu.isNaN() || cpu < 0 ? null : cpu,
        ManagementFactory.getThreadMXBean().getThreadCount(),
        disk.getUsableSpace(),
        disk.getTotalSpace());
  }

  private Http http() {
    long requests = 0;
    long client = 0;
    long server = 0;
    double totalMs = 0;
    double maxMs = 0;
    for (Timer t : registry.find("http.server.requests").timers()) {
      String uri = t.getId().getTag("uri");
      if (uri != null && uri.startsWith("/actuator")) {
        continue;
      }
      long count = t.count();
      requests += count;
      totalMs += t.totalTime(TimeUnit.MILLISECONDS);
      maxMs = Math.max(maxMs, t.max(TimeUnit.MILLISECONDS));
      String outcome = t.getId().getTag("outcome");
      if ("CLIENT_ERROR".equals(outcome)) {
        client += count;
      } else if ("SERVER_ERROR".equals(outcome)) {
        server += count;
      }
    }
    return new Http(
        requests, client, server, requests == 0 ? null : round(totalMs / requests), round(maxMs));
  }

  private Logins logins() {
    long success = 0;
    long failed = 0;
    long locked = 0;
    for (Counter c : registry.find(LoginMetrics.METER).counters()) {
      String outcome = c.getId().getTag("outcome");
      long n = (long) c.count();
      if ("success".equals(outcome)) {
        success += n;
      } else {
        failed += n;
        if ("account_locked".equals(outcome)) {
          locked += n;
        }
      }
    }
    return new Logins(success, failed, locked);
  }

  private long activeSessions(Instant now) {
    try {
      Long n =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM user_sessions WHERE revoked_at IS NULL AND expires_at > ?",
              Long.class,
              Timestamp.from(now));
      return n == null ? 0 : n;
    } catch (RuntimeException e) {
      log.warn("Estado do sistema: não foi possível contar as sessões: {}", e.getMessage());
      return 0;
    }
  }

  private List<Job> jobs() {
    return scheduledTasks.stream()
        .flatMap(holder -> holder.getScheduledTasks().stream())
        .map(SystemStatusService::job)
        .sorted(Comparator.comparing(Job::label))
        .toList();
  }

  private static Job job(ScheduledTask task) {
    var runnable = task.getTask().getRunnable();
    String name =
        runnable instanceof ScheduledMethodRunnable m
            ? m.getMethod().getDeclaringClass().getSimpleName()
            : className(runnable.toString());
    String cron = task.getTask() instanceof CronTask c ? c.getExpression() : null;
    var outcome = task.getTask().getLastExecutionOutcome();
    String error = null;
    if (outcome.throwable() != null) {
      // Só a classe: a mensagem pode conter dados.
      error = outcome.throwable().getClass().getSimpleName();
    }
    return new Job(
        name,
        JOB_LABELS.getOrDefault(name, name),
        cron,
        task.nextExecution(),
        outcome.status() == null || "NONE".equals(outcome.status().name())
            ? null
            : outcome.status().name(),
        outcome.executionTime(),
        error);
  }

  /**
   * O Spring embrulha o método agendado (registo do resultado), mas o {@code toString} continua a
   * ser {@code pacote.Classe.metodo}: fica a classe.
   */
  static String className(String qualifiedMethod) {
    String[] parts = qualifiedMethod.split("\\.");
    return parts.length >= 2 ? parts[parts.length - 2] : qualifiedMethod;
  }

  private long count(String sql) {
    Long n = jdbc.queryForObject(sql, Long.class);
    return n == null ? 0 : n;
  }

  private Double gauge(String name) {
    Gauge g = registry.find(name).gauge();
    return g == null ? null : g.value();
  }

  private Integer gaugeInt(String name) {
    Double v = gauge(name);
    return v == null || v.isNaN() ? null : (int) Math.round(v);
  }

  private static Instant toInstant(Object value) {
    return switch (value) {
      case LocalDateTime ldt -> ldt.toInstant(ZoneOffset.UTC);
      case Timestamp ts -> ts.toInstant();
      case null, default -> null;
    };
  }

  private static double round(double v) {
    return Math.round(v * 10) / 10.0;
  }
}
