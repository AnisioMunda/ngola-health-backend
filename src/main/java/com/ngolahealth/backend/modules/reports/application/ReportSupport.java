package com.ngolahealth.backend.modules.reports.application;

import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Base dos relatórios. As agregações usam SQL directo (não passam pelo filtro de hospital do
 * Hibernate): cada consulta tem de filtrar por {@code hospital_id = :hospital}, que o período
 * preenche sempre com o hospital do utilizador autenticado.
 */
@Component
@RequiredArgsConstructor
class ReportSupport {

  final NamedParameterJdbcTemplate jdbc;
  private final CurrentUserProvider currentUser;
  private final AppProperties app;
  private final Clock clock;

  ReportPeriod period(LocalDate from, LocalDate to) {
    var user = currentUser.get();
    if (user.hospitalId() == null) {
      throw new ForbiddenException(
          "HOSPITAL_SCOPE_REQUIRED",
          "Os relatórios são de um hospital: entre com um utilizador do hospital.");
    }
    var today = today();
    var end = to != null ? to : today;
    var start = from != null ? from : end.minusDays(29);
    return new ReportPeriod(user.hospitalId(), start, end, app.timeZone());
  }

  java.time.Instant now() {
    return clock.instant();
  }

  LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), app.timeZone());
  }

  long count(String sql, MapSqlParameterSource p) {
    Long v = jdbc.queryForObject(sql, p, Long.class);
    return v == null ? 0 : v;
  }

  Double dbl(String sql, MapSqlParameterSource p) {
    Number v = jdbc.queryForObject(sql, p, Number.class);
    return v == null ? null : round1(v.doubleValue());
  }

  BigDecimal money(String sql, MapSqlParameterSource p) {
    BigDecimal v = jdbc.queryForObject(sql, p, BigDecimal.class);
    return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2);
  }

  /** Mapa "chave → contagem" de um SELECT com duas colunas, pela ordem do SQL. */
  Map<String, Long> grouped(String sql, MapSqlParameterSource p) {
    var map = new LinkedHashMap<String, Long>();
    jdbc.query(
        sql,
        p,
        rs -> {
          map.put(rs.getString(1), rs.getLong(2));
        });
    return map;
  }

  static Double round1(double v) {
    return Math.round(v * 10.0) / 10.0;
  }

  static Double percent(long part, long total) {
    return total == 0 ? null : round1(100.0 * part / total);
  }
}
