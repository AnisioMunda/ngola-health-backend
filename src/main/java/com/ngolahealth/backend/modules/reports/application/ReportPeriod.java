package com.ngolahealth.backend.modules.reports.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Período de um relatório em dias locais (inclusive). As datas na BD estão em UTC; {@code offset}
 * converte-as para o dia local nas agregações diárias.
 */
public record ReportPeriod(UUID hospitalId, LocalDate from, LocalDate to, ZoneId zone) {

  public static final int MAX_DAYS = 366;

  public ReportPeriod {
    if (to.isBefore(from)) {
      throw new BadRequestException("INVALID_RANGE", "A data final é anterior à inicial.");
    }
    if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
      throw new BadRequestException("RANGE_TOO_LONG", "O período não pode exceder um ano.");
    }
  }

  public Instant start() {
    return from.atStartOfDay(zone).toInstant();
  }

  public Instant end() {
    return to.plusDays(1).atStartOfDay(zone).toInstant();
  }

  public long days() {
    return ChronoUnit.DAYS.between(from, to) + 1;
  }

  /** Diferença (segundos) entre o fuso local e UTC, para agrupar por dia local na BD. */
  public int offsetSeconds() {
    return zone.getRules().getOffset(start()).getTotalSeconds();
  }

  /** Parâmetros comuns: hospital (sempre!), início, fim e desvio horário. */
  public MapSqlParameterSource params() {
    return new MapSqlParameterSource()
        .addValue("hospital", hospitalId.toString())
        .addValue("start", Timestamp.from(start()))
        .addValue("end", Timestamp.from(end()))
        .addValue("startDate", from)
        .addValue("endDate", to)
        .addValue("offset", offsetSeconds());
  }
}
