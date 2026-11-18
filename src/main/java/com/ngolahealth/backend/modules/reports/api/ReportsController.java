package com.ngolahealth.backend.modules.reports.api;

import com.ngolahealth.backend.modules.reports.api.ReportDtos.Dashboard;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.FinancialReport;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.OccupancyReport;
import com.ngolahealth.backend.modules.reports.application.DashboardService;
import com.ngolahealth.backend.modules.reports.application.FinancialReportService;
import com.ngolahealth.backend.modules.reports.application.OccupancyReportService;
import com.ngolahealth.backend.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Relatórios", description = "Dashboard executivo, ocupação e financeiro")
public class ReportsController {

  private final DashboardService dashboard;
  private final OccupancyReportService occupancy;
  private final FinancialReportService financial;

  @GetMapping("/dashboard")
  @PreAuthorize(Authorities.REPORT_READERS)
  @Operation(summary = "Indicadores do período (por omissão, últimos 30 dias)")
  public Dashboard dashboard(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return dashboard.dashboard(from, to);
  }

  @GetMapping("/occupancy")
  @PreAuthorize(Authorities.REPORT_READERS)
  public OccupancyReport occupancy(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return occupancy.report(from, to);
  }

  @GetMapping("/occupancy.csv")
  @PreAuthorize(Authorities.REPORT_READERS)
  public ResponseEntity<byte[]> occupancyCsv(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    var r = occupancy.report(from, to);
    var rows = new ArrayList<List<Object>>();
    var all = new ArrayList<>(r.wards());
    all.add(r.total());
    for (var w : all) {
      rows.add(
          java.util.Arrays.asList(
              w.wardName(),
              w.beds(),
              w.occupiedNow(),
              w.admissions(),
              w.discharges(),
              w.bedDays(),
              w.occupancyPercent(),
              w.avgLengthOfStayDays()));
    }
    return CsvWriter.file(
        "ocupacao_" + r.from() + "_" + r.to() + ".csv",
        List.of(
            "Enfermaria",
            "Camas",
            "Ocupadas agora",
            "Entradas",
            "Altas",
            "Dias-cama",
            "Ocupação %",
            "Demora média (dias)"),
        rows);
  }

  @GetMapping("/financial")
  @PreAuthorize(Authorities.FINANCIAL_REPORT_READERS)
  public FinancialReport financial(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return financial.report(from, to);
  }

  @GetMapping("/financial.csv")
  @PreAuthorize(Authorities.FINANCIAL_REPORT_READERS)
  public ResponseEntity<byte[]> financialCsv(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    var r = financial.report(from, to);
    var rows = new ArrayList<List<Object>>();
    for (var d : r.daily()) {
      rows.add(List.of(d.date().toString(), d.invoiced(), d.received()));
    }
    rows.add(List.of("Total", r.summary().invoiced(), r.summary().received()));
    return CsvWriter.file(
        "financeiro_" + r.from() + "_" + r.to() + ".csv",
        List.of("Dia", "Facturado (AOA)", "Recebido (AOA)"),
        rows);
  }
}
