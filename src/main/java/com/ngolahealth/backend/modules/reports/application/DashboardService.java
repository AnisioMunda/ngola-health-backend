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
import java.math.BigDecimal;
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
  private final FinancialReportService finance;
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
        finance.summary(period),
        daily(period));
  }

  private PatientStats patients(org.springframework.jdbc.core.namedparam.MapSqlParameterSource p) {
    return new PatientStats(
        r.count("SELECT COUNT(*) FROM patients WHERE hospital_id = :hospital AND active", p),
        r.count(
            "SELECT COUNT(*) FROM patients WHERE hospital_id = :hospital AND created_at >= :start AND created_at < :end",
            p));
  }

  private EpisodeStats episodes(org.springframework.jdbc.core.namedparam.MapSqlParameterSource p) {
    var where =
        " FROM episodes WHERE hospital_id = :hospital AND scheduled_at >= :start AND scheduled_at < :end";
    var byType =
        r.grouped("SELECT type, COUNT(*)" + where + " GROUP BY type ORDER BY COUNT(*) DESC", p);
    var byStatus = r.grouped("SELECT status, COUNT(*)" + where + " GROUP BY status", p);
    return new EpisodeStats(
        byType.values().stream().mapToLong(Long::longValue).sum(),
        byStatus.getOrDefault("COMPLETED", 0L),
        byType,
        byStatus);
  }

  /** Espera = da classificação à chamada; cumprimento do tempo-alvo por prioridade (Manchester). */
  private TriageStats triage(org.springframework.jdbc.core.namedparam.MapSqlParameterSource p) {
    var where =
        " FROM triage_records WHERE hospital_id = :hospital AND triaged_at >= :start AND triaged_at < :end";
    var byPriority = r.grouped("SELECT priority, COUNT(*)" + where + " GROUP BY priority", p);
    var ordered = new LinkedHashMap<String, Long>();
    for (var prio : TriagePriority.values()) {
      if (byPriority.containsKey(prio.name())) {
        ordered.put(prio.name(), byPriority.get(prio.name()));
      }
    }
    var wait = "TIMESTAMPDIFF(SECOND, triaged_at, called_at) / 60.0";
    var target =
        Stream.of(TriagePriority.values())
            .map(t -> "WHEN '" + t.name() + "' THEN " + triage.targetFor(t))
            .collect(Collectors.joining(" ", "CASE priority ", " END"));
    var called = where + " AND called_at IS NOT NULL";
    long calledCount = r.count("SELECT COUNT(*)" + called, p);
    long within = r.count("SELECT COUNT(*)" + called + " AND " + wait + " <= " + target, p);
    var byPriorityWait = new LinkedHashMap<String, Double>();
    r.jdbc.query(
        "SELECT priority, AVG(" + wait + ")" + called + " GROUP BY priority",
        p,
        rs -> {
          byPriorityWait.put(rs.getString(1), ReportSupport.round1(rs.getDouble(2)));
        });
    var avgWait = new LinkedHashMap<String, Double>();
    for (var prio : TriagePriority.values()) {
      if (byPriorityWait.containsKey(prio.name())) {
        avgWait.put(prio.name(), byPriorityWait.get(prio.name()));
      }
    }
    return new TriageStats(
        ordered.values().stream().mapToLong(Long::longValue).sum(),
        ordered,
        r.dbl("SELECT AVG(" + wait + ")" + called, p),
        ReportSupport.percent(within, calledCount),
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

  private LabStats lab(org.springframework.jdbc.core.namedparam.MapSqlParameterSource p) {
    var where =
        " FROM lab_requests WHERE hospital_id = :hospital AND requested_at >= :start AND requested_at < :end";
    var tat = "TIMESTAMPDIFF(MINUTE, requested_at, completed_at) / 60.0";
    long items =
        r.count(
            "SELECT COUNT(*) FROM lab_request_items i JOIN lab_requests q ON q.id = i.request_id"
                + " WHERE q.hospital_id = :hospital AND q.requested_at >= :start AND q.requested_at < :end"
                + " AND i.status = 'VALIDATED'",
            p);
    long abnormal =
        r.count(
            "SELECT COUNT(*) FROM lab_request_items i JOIN lab_requests q ON q.id = i.request_id"
                + " WHERE q.hospital_id = :hospital AND q.requested_at >= :start AND q.requested_at < :end"
                + " AND i.status = 'VALIDATED' AND i.flag IN ('LOW', 'HIGH', 'ABNORMAL')",
            p);
    return new LabStats(
        r.count("SELECT COUNT(*)" + where + " AND status <> 'CANCELLED'", p),
        r.count("SELECT COUNT(*)" + where + " AND status = 'COMPLETED'", p),
        r.dbl("SELECT AVG(" + tat + ")" + where + " AND status = 'COMPLETED'", p),
        r.dbl(
            "SELECT AVG(" + tat + ")" + where + " AND status = 'COMPLETED' AND priority = 'URGENT'",
            p),
        ReportSupport.percent(abnormal, items));
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
    var revenue = finance.invoicedPerDay(period);
    var result = new ArrayList<DayPoint>();
    for (var d = period.from(); !d.isAfter(period.to()); d = d.plusDays(1)) {
      result.add(
          new DayPoint(
              d,
              episodes.getOrDefault(d, 0L),
              admissions.getOrDefault(d, 0L),
              triaged.getOrDefault(d, 0L),
              revenue.getOrDefault(d, BigDecimal.ZERO.setScale(2))));
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
