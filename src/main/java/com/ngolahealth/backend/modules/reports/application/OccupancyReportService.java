package com.ngolahealth.backend.modules.reports.application;

import com.ngolahealth.backend.modules.reports.api.ReportDtos.OccupancyReport;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.WardOccupancy;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ocupação por enfermaria. Os dias-cama contam o tempo real em cada enfermaria, seguindo as
 * transferências; internamentos anulados (registos por engano) não contam.
 */
@Service
@RequiredArgsConstructor
public class OccupancyReportService {

  private static final double DAY_SECONDS = 86_400.0;

  private final ReportSupport r;

  private record Stay(UUID id, UUID ward, Instant admitted, Instant end, Instant dischargedAt) {}

  private record Move(UUID admission, UUID fromWard, UUID toWard, Instant at) {}

  private static final class Acc {
    long admissions;
    long discharges;
    double bedSeconds;
    double stayDaysOfDischarged;
  }

  @Transactional(readOnly = true)
  public OccupancyReport report(LocalDate from, LocalDate to) {
    var period = r.period(from, to);
    var p = period.params();
    var start = period.start();
    var end = period.end();
    var now = r.now();

    var wards = new LinkedHashMap<UUID, String>();
    r.jdbc.query(
        "SELECT id, name FROM wards WHERE hospital_id = :hospital AND active ORDER BY name",
        p,
        rs -> {
          wards.put(UUID.fromString(rs.getString(1)), rs.getString(2));
        });
    var beds = new HashMap<UUID, long[]>(); // [activas, ocupadas agora]
    r.jdbc.query(
        "SELECT ward_id, COUNT(*), SUM(status = 'OCCUPIED') FROM beds WHERE hospital_id = :hospital AND active GROUP BY ward_id",
        p,
        rs -> {
          beds.put(UUID.fromString(rs.getString(1)), new long[] {rs.getLong(2), rs.getLong(3)});
        });

    var stays =
        r.jdbc.query(
            "SELECT id, ward_id, admitted_at, discharged_at FROM admissions WHERE hospital_id = :hospital"
                + " AND status <> 'CANCELLED' AND admitted_at < :end AND (discharged_at IS NULL OR discharged_at > :start)",
            p,
            (rs, i) -> {
              Timestamp discharged = rs.getTimestamp(4);
              return new Stay(
                  UUID.fromString(rs.getString(1)),
                  UUID.fromString(rs.getString(2)),
                  rs.getTimestamp(3).toInstant(),
                  discharged == null ? now : discharged.toInstant(),
                  discharged == null ? null : discharged.toInstant());
            });
    var moves = new HashMap<UUID, List<Move>>();
    if (!stays.isEmpty()) {
      r.jdbc.query(
          "SELECT t.admission_id, t.from_ward_id, t.to_ward_id, t.transferred_at FROM admission_transfers t"
              + " JOIN admissions a ON a.id = t.admission_id"
              + " WHERE a.hospital_id = :hospital AND a.status <> 'CANCELLED' AND a.admitted_at < :end"
              + " AND (a.discharged_at IS NULL OR a.discharged_at > :start) ORDER BY t.transferred_at",
          p,
          rs -> {
            var m =
                new Move(
                    UUID.fromString(rs.getString(1)),
                    UUID.fromString(rs.getString(2)),
                    UUID.fromString(rs.getString(3)),
                    rs.getTimestamp(4).toInstant());
            moves.computeIfAbsent(m.admission(), k -> new ArrayList<>()).add(m);
          });
    }

    var acc = new HashMap<UUID, Acc>();
    for (var s : stays) {
      var history = moves.getOrDefault(s.id(), List.of());
      // Segmentos: [entrada → 1.ª transferência] na enfermaria de origem, e assim sucessivamente.
      var ward = history.isEmpty() ? s.ward() : history.getFirst().fromWard();
      var segmentStart = s.admitted();
      if (!s.admitted().isBefore(start) && s.admitted().isBefore(end)) {
        acc.computeIfAbsent(ward, k -> new Acc()).admissions++;
      }
      for (var m : history) {
        addSeconds(acc, ward, segmentStart, m.at(), start, end);
        ward = m.toWard();
        segmentStart = m.at();
      }
      addSeconds(acc, ward, segmentStart, s.end(), start, end);
      if (s.dischargedAt() != null
          && !s.dischargedAt().isBefore(start)
          && s.dischargedAt().isBefore(end)) {
        var a = acc.computeIfAbsent(ward, k -> new Acc());
        a.discharges++;
        a.stayDaysOfDischarged +=
            Duration.between(s.admitted(), s.dischargedAt()).toSeconds() / DAY_SECONDS;
      }
    }

    long days = period.days();
    var rows = new ArrayList<WardOccupancy>();
    for (var w : wards.entrySet()) {
      rows.add(row(w.getKey(), w.getValue(), beds.get(w.getKey()), acc.get(w.getKey()), days));
    }
    rows.sort(Comparator.comparing(WardOccupancy::wardName));
    var total = new Acc();
    long[] totalBeds = {0, 0};
    for (var w : wards.keySet()) {
      var a = acc.get(w);
      if (a != null) {
        total.admissions += a.admissions;
        total.discharges += a.discharges;
        total.bedSeconds += a.bedSeconds;
        total.stayDaysOfDischarged += a.stayDaysOfDischarged;
      }
      var b = beds.get(w);
      if (b != null) {
        totalBeds[0] += b[0];
        totalBeds[1] += b[1];
      }
    }
    return new OccupancyReport(
        period.from(), period.to(), days, rows, row(null, "Total", totalBeds, total, days));
  }

  private static void addSeconds(
      Map<UUID, Acc> acc, UUID ward, Instant from, Instant to, Instant start, Instant end) {
    var a = from.isBefore(start) ? start : from;
    var b = to.isAfter(end) ? end : to;
    if (b.isAfter(a)) {
      acc.computeIfAbsent(ward, k -> new Acc()).bedSeconds += Duration.between(a, b).toSeconds();
    }
  }

  private static WardOccupancy row(UUID id, String name, long[] beds, Acc a, long days) {
    long active = beds == null ? 0 : beds[0];
    long occupied = beds == null ? 0 : beds[1];
    var acc = a == null ? new Acc() : a;
    double bedDays = ReportSupport.round1(acc.bedSeconds / DAY_SECONDS);
    return new WardOccupancy(
        id,
        name,
        active,
        occupied,
        acc.admissions,
        acc.discharges,
        bedDays,
        active == 0 ? null : ReportSupport.round1(100.0 * bedDays / (active * days)),
        acc.discharges == 0
            ? null
            : ReportSupport.round1(acc.stayDaysOfDischarged / acc.discharges));
  }
}
