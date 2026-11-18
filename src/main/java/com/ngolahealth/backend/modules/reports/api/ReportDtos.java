package com.ngolahealth.backend.modules.reports.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReportDtos {

  private ReportDtos() {}

  public record PatientStats(long active, long newInPeriod) {}

  public record EpisodeStats(
      long total, long completed, Map<String, Long> byType, Map<String, Long> byStatus) {}

  /**
   * @param withinTargetPercent percentagem de doentes chamados dentro do tempo-alvo de Manchester
   *     (só os que já foram chamados); null sem dados
   */
  public record TriageStats(
      long total,
      Map<String, Long> byPriority,
      Double avgWaitMinutes,
      Double withinTargetPercent,
      Map<String, Double> avgWaitByPriority) {}

  public record AppointmentStats(
      long total, long checkedIn, long noShow, long cancelled, Double noShowPercent) {}

  public record InpatientStats(
      long activeBeds,
      long occupiedBeds,
      Double occupancyPercent,
      long admissions,
      long discharges,
      long deaths,
      Double avgLengthOfStayDays) {}

  public record LabStats(
      long requests,
      long completed,
      Double avgTurnaroundHours,
      Double urgentAvgTurnaroundHours,
      Double abnormalPercent) {}

  public record PharmacyStats(long lowStock, long expiringIn30Days, long expiredWithBalance) {}

  public record DayPoint(LocalDate date, long episodes, long admissions, long triage) {}

  public record Dashboard(
      LocalDate from,
      LocalDate to,
      PatientStats patients,
      EpisodeStats episodes,
      TriageStats triage,
      AppointmentStats appointments,
      InpatientStats inpatient,
      LabStats lab,
      PharmacyStats pharmacy,
      List<DayPoint> daily) {}

  // ---------- Ocupação ----------

  /**
   * @param bedDays dias-cama ocupados no período (soma da permanência de cada internamento dentro
   *     do período)
   * @param occupancyPercent dias-cama ocupados ÷ (camas activas × dias do período)
   */
  public record WardOccupancy(
      UUID wardId,
      String wardName,
      long beds,
      long occupiedNow,
      long admissions,
      long discharges,
      double bedDays,
      Double occupancyPercent,
      Double avgLengthOfStayDays) {}

  public record OccupancyReport(
      LocalDate from, LocalDate to, long days, List<WardOccupancy> wards, WardOccupancy total) {}
}
