package com.ngolahealth.backend.modules.reports.application;

import com.ngolahealth.backend.config.properties.TriageProperties;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.AppointmentStats;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.Dashboard;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.DayPoint;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.EpisodeStats;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.InpatientStats;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.LabStats;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.PatientStats;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.PharmacyStats;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.TriageStats;
import com.ngolahealth.backend.modules.triage.domain.TriagePriority;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Indicadores do hospital num período (por omissão, os últimos 30 dias). */
@Service
@RequiredArgsConstructor
public class DashboardService {

  private final ReportSupport r;
  private final TriageProperties triage;

  @Transactional(readOnly = true)
  public Dashboard dashboard(LocalDate from, LocalDate to) {
    var period = r.period(from, to);
    var p = period.params();
    return new Dashboard(
        period.from(),
        period.to(),
        patients(p),
        episodes(p),
        triage(p),
        appointments(p),
        inpatient(p),
        lab(p),
        pharmacy(p),
        daily(period));
  }

  private PatientStats patients(org.springframework.jdbc.core.namedparam.MapSqlParameterSource p) {
    return new PatientStats(
        r.count("SELECT COUNT(*) FROM patients WHERE hospital_id = :hospital AND active", p),
        r.count(
            "SELECT COUNT(*) FROM patients WHERE hospital_id = :hospital AND created_at >= :start AND created_at < :end",
            p));
  }

  /** Uma só passagem: contagens por (tipo, estado), somadas em Java. */
  private EpisodeStats episodes(org.springframework.jdbc.core.namedparam.MapSqlParameterSource p) {
    var byType = new HashMap<String, Long>();
    var byStatus = new LinkedHashMap<String, Long>();
    r.jdbc.query(
        "SELECT type, status, COUNT(*) FROM episodes WHERE hospital_id = :hospital"
            + " AND scheduled_at >= :start AND scheduled_at < :end GROUP BY type, status",
        p,
        rs -> {
          long n = rs.getLong(3);
          byType.merge(rs.getString(1), n, Long::sum);
          byStatus.merge(rs.getString(2), n, Long::sum);
        });
    var typesByCount = new LinkedHashMap<String, Long>();
    byType.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .forEach(e -> typesByCount.put(e.getKey(), e.getValue()));
    return new EpisodeStats(
        byType.values().stream().mapToLong(Long::longValue).sum(),
        byStatus.getOrDefault("COMPLETED", 0L),
        typesByCount,
        byStatus);
  }

  /**
   * Espera = da classificação à chamada; cumprimento do tempo-alvo por prioridade (Manchester). Uma
   * só passagem pelo índice (hospital, data, prioridade, chamada): por prioridade, o total, os já
   * chamados, a soma das esperas e os chamados dentro do tempo-alvo.
   */
  private TriageStats triage(org.springframework.jdbc.core.namedparam.MapSqlParameterSource p) {
    var wait = "TIMESTAMPDIFF(SECOND, triaged_at, called_at) / 60.0";
    var target =
        Stream.of(TriagePriority.values())
            .map(t -> "WHEN '" + t.name() + "' THEN " + triage.targetFor(t))
            .collect(Collectors.joining(" ", "CASE priority ", " END"));
    var rows = new HashMap<String, double[]>();
    r.jdbc.query(
        "SELECT priority, COUNT(*), COUNT(called_at), SUM("
            + wait
            + "), SUM(called_at IS NOT NULL AND "
            + wait
            + " <= "
            + target
            + ") FROM triage_records WHERE hospital_id = :hospital"
            + " AND triaged_at >= :start AND triaged_at < :end GROUP BY priority",
        p,
        rs -> {
          rows.put(
              rs.getString(1),
              new double[] {rs.getLong(2), rs.getLong(3), rs.getDouble(4), rs.getLong(5)});
        });
    var byPriority = new LinkedHashMap<String, Long>();
    var avgWait = new LinkedHashMap<String, Double>();
    long total = 0;
    long called = 0;
    long within = 0;
    double waitSum = 0;
    for (var prio : TriagePriority.values()) {
      var row = rows.get(prio.name());
      if (row == null) {
        continue;
      }
      byPriority.put(prio.name(), (long) row[0]);
      if (row[1] > 0) {
        avgWait.put(prio.name(), ReportSupport.round1(row[2] / row[1]));
      }
      total += (long) row[0];
      called += (long) row[1];
      waitSum += row[2];
      within += (long) row[3];
    }
    return new TriageStats(
        total,
        byPriority,
        called == 0 ? null : ReportSupport.round1(waitSum / called),
        ReportSupport.percent(within, called),
        avgWait);
  }

  private AppointmentStats appointments(
      org.springframework.jdbc.core.namedparam.MapSqlParameterSource p) {
    var byStatus =
        r.grouped(
            "SELECT status, COUNT(*) FROM appointments WHERE hospital_id = :hospital"
                + " AND appointment_date BETWEEN :startDate AND :endDate GROUP BY status",
            p);
    long checkedIn = byStatus.getOrDefault("CHECKED_IN", 0L);
    long noShow = byStatus.getOrDefault("NO_SHOW", 0L);
    return new AppointmentStats(
        byStatus.values().stream().mapToLong(Long::longValue).sum(),
        checkedIn,
        noShow,
        byStatus.getOrDefault("CANCELLED", 0L),
        // Faltas sobre as consultas que já tinham de acontecer (compareceu ou faltou).
        ReportSupport.percent(noShow, checkedIn + noShow));
  }

  private InpatientStats inpatient(
      org.springframework.jdbc.core.namedparam.MapSqlParameterSource p) {
    long beds = r.count("SELECT COUNT(*) FROM beds WHERE hospital_id = :hospital AND active", p);
    long occupied =
        r.count(
            "SELECT COUNT(*) FROM beds WHERE hospital_id = :hospital AND active AND status = 'OCCUPIED'",
            p);
    var discharged =
        " FROM admissions WHERE hospital_id = :hospital AND status = 'DISCHARGED'"
            + " AND discharged_at >= :start AND discharged_at < :end";
    return new InpatientStats(
        beds,
        occupied,
        ReportSupport.percent(occupied, beds),
        r.count(
            "SELECT COUNT(*) FROM admissions WHERE hospital_id = :hospital AND status <> 'CANCELLED'"
                + " AND admitted_at >= :start AND admitted_at < :end",
            p),
        r.count("SELECT COUNT(*)" + discharged, p),
        r.count("SELECT COUNT(*)" + discharged + " AND discharge_condition = 'DECEASED'", p),
        r.dbl(
            "SELECT AVG(TIMESTAMPDIFF(MINUTE, admitted_at, discharged_at) / 1440.0)" + discharged,
            p));
  }

  /** Duas passagens: os pedidos (contagens e tempos de resposta) e os exames validados. */
  private LabStats lab(org.springframework.jdbc.core.namedparam.MapSqlParameterSource p) {
    var tat = "TIMESTAMPDIFF(MINUTE, requested_at, completed_at) / 60.0";
    var items =
        r.jdbc.queryForObject(
            "SELECT COUNT(*), COALESCE(SUM(i.flag IN ('LOW', 'HIGH', 'ABNORMAL')), 0)"
                + " FROM lab_request_items i JOIN lab_requests q ON q.id = i.request_id"
                + " WHERE q.hospital_id = :hospital AND q.requested_at >= :start AND q.requested_at < :end"
                + " AND i.status = 'VALIDATED'",
            p,
            (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)});
    return r.jdbc.queryForObject(
        "SELECT COALESCE(SUM(status <> 'CANCELLED'), 0), COALESCE(SUM(status = 'COMPLETED'), 0),"
            + " AVG(CASE WHEN status = 'COMPLETED' THEN "
            + tat
            + " END), AVG(CASE WHEN status = 'COMPLETED' AND priority = 'URGENT' THEN "
            + tat
            + " END) FROM lab_requests WHERE hospital_id = :hospital"
            + " AND requested_at >= :start AND requested_at < :end",
        p,
        (rs, n) ->
            new LabStats(
                rs.getLong(1),
                rs.getLong(2),
                nullableRound(rs, 3),
                nullableRound(rs, 4),
                ReportSupport.percent(items[1], items[0])));
  }

  private static Double nullableRound(java.sql.ResultSet rs, int column)
      throws java.sql.SQLException {
    double v = rs.getDouble(column);
    return rs.wasNull() ? null : ReportSupport.round1(v);
  }

  private PharmacyStats pharmacy(org.springframework.jdbc.core.namedparam.MapSqlParameterSource p) {
    p.addValue("today", r.today()).addValue("in30", r.today().plusDays(30));
    return new PharmacyStats(
        r.count(
            "SELECT COUNT(*) FROM medications m WHERE m.hospital_id = :hospital AND m.active"
                + " AND COALESCE((SELECT SUM(b.quantity_available) FROM stock_batches b"
                + " WHERE b.medication_id = m.id AND b.expiry_date >= :today), 0) <= m.min_stock_level",
            p),
        r.count(
            "SELECT COUNT(*) FROM stock_batches WHERE hospital_id = :hospital AND quantity_available > 0"
                + " AND expiry_date >= :today AND expiry_date <= :in30",
            p),
        r.count(
            "SELECT COUNT(*) FROM stock_batches WHERE hospital_id = :hospital AND quantity_available > 0"
                + " AND expiry_date < :today",
            p));
  }

  /** Série diária (dias sem movimento aparecem a zero). */
  private List<DayPoint> daily(ReportPeriod period) {
    var p = period.params();
    var localDay = "DATE(DATE_ADD(%s, INTERVAL :offset SECOND))";
    var episodes =
        perDay(
            "SELECT "
                + localDay.formatted("scheduled_at")
                + " d, COUNT(*) FROM episodes"
                + " WHERE hospital_id = :hospital AND scheduled_at >= :start AND scheduled_at < :end GROUP BY d",
            p);
    var admissions =
        perDay(
            "SELECT "
                + localDay.formatted("admitted_at")
                + " d, COUNT(*) FROM admissions"
                + " WHERE hospital_id = :hospital AND status <> 'CANCELLED' AND admitted_at >= :start AND admitted_at < :end GROUP BY d",
            p);
    var triaged =
        perDay(
            "SELECT "
                + localDay.formatted("triaged_at")
                + " d, COUNT(*) FROM triage_records"
                + " WHERE hospital_id = :hospital AND triaged_at >= :start AND triaged_at < :end GROUP BY d",
            p);
    var result = new ArrayList<DayPoint>();
    for (var d = period.from(); !d.isAfter(period.to()); d = d.plusDays(1)) {
      result.add(
          new DayPoint(
              d,
              episodes.getOrDefault(d, 0L),
              admissions.getOrDefault(d, 0L),
              triaged.getOrDefault(d, 0L)));
    }
    return result;
  }

  private Map<LocalDate, Long> perDay(
      String sql, org.springframework.jdbc.core.namedparam.MapSqlParameterSource p) {
    var map = new HashMap<LocalDate, Long>();
    r.jdbc.query(
        sql,
        p,
        rs -> {
          map.put(rs.getDate(1).toLocalDate(), rs.getLong(2));
        });
    return map;
  }
}
