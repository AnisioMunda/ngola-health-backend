package com.ngolahealth.backend.modules.reports.application;

import com.ngolahealth.backend.modules.reports.api.ReportDtos.Amount;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.FinanceStats;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.FinancialDay;
import com.ngolahealth.backend.modules.reports.api.ReportDtos.FinancialReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facturação do período: facturado (FT e FR emitidas, não anuladas), creditado (notas de crédito),
 * recebido (pagamentos) e em dívida.
 */
@Service
@RequiredArgsConstructor
public class FinancialReportService {

  /** Facturas e facturas-recibo emitidas no período e não anuladas. */
  static final String ISSUED =
      " FROM invoices i WHERE i.hospital_id = :hospital AND i.document_type IN ('FT', 'FR')"
          + " AND i.status <> 'CANCELLED' AND i.issued_at >= :start AND i.issued_at < :end";

  private final ReportSupport r;

  @Transactional(readOnly = true)
  public FinancialReport report(LocalDate from, LocalDate to) {
    var period = r.period(from, to);
    var p = period.params();
    var invoiced = invoicedPerDay(period);
    var received =
        perDay(
            "SELECT DATE(DATE_ADD(received_at, INTERVAL :offset SECOND)) d, SUM(amount) FROM payments"
                + " WHERE hospital_id = :hospital AND received_at >= :start AND received_at < :end GROUP BY d",
            p);
    var daily = new ArrayList<FinancialDay>();
    for (var d = period.from(); !d.isAfter(period.to()); d = d.plusDays(1)) {
      daily.add(
          new FinancialDay(d, invoiced.getOrDefault(d, zero()), received.getOrDefault(d, zero())));
    }
    return new FinancialReport(
        period.from(),
        period.to(),
        summary(period),
        amounts(
            "SELECT method, SUM(amount), COUNT(*) FROM payments WHERE hospital_id = :hospital"
                + " AND received_at >= :start AND received_at < :end GROUP BY method ORDER BY SUM(amount) DESC",
            p),
        amounts(
            "SELECT COALESCE(sp.category, 'OTHER') c, SUM(it.gross_amount), COUNT(*)"
                + " FROM invoice_items it JOIN invoices i ON i.id = it.invoice_id"
                + " LEFT JOIN service_prices sp ON sp.id = it.service_price_id"
                + " WHERE i.hospital_id = :hospital AND i.document_type IN ('FT', 'FR') AND i.status <> 'CANCELLED'"
                + " AND i.issued_at >= :start AND i.issued_at < :end GROUP BY c ORDER BY SUM(it.gross_amount) DESC",
            p),
        amounts(
            "SELECT it.description, SUM(it.gross_amount), SUM(it.quantity)"
                + " FROM invoice_items it JOIN invoices i ON i.id = it.invoice_id"
                + " WHERE i.hospital_id = :hospital AND i.document_type IN ('FT', 'FR') AND i.status <> 'CANCELLED'"
                + " AND i.issued_at >= :start AND i.issued_at < :end"
                + " GROUP BY it.description ORDER BY SUM(it.gross_amount) DESC LIMIT 10",
            p),
        daily);
  }

  FinanceStats summary(ReportPeriod period) {
    var p = period.params();
    var invoiced = r.money("SELECT SUM(i.gross_total)" + ISSUED, p);
    var credited =
        r.money(
            "SELECT SUM(gross_total) FROM invoices WHERE hospital_id = :hospital AND document_type = 'NC'"
                + " AND issued_at >= :start AND issued_at < :end",
            p);
    return new FinanceStats(
        invoiced,
        credited,
        invoiced.subtract(credited),
        r.money(
            "SELECT SUM(amount) FROM payments WHERE hospital_id = :hospital"
                + " AND received_at >= :start AND received_at < :end",
            p),
        r.money(
            "SELECT SUM(gross_total - paid_total) FROM invoices WHERE hospital_id = :hospital"
                + " AND document_type = 'FT' AND status IN ('ISSUED', 'PARTIALLY_PAID')",
            p),
        r.count("SELECT COUNT(*)" + ISSUED, p));
  }

  Map<LocalDate, BigDecimal> invoicedPerDay(ReportPeriod period) {
    return perDay(
        "SELECT DATE(DATE_ADD(i.issued_at, INTERVAL :offset SECOND)) d, SUM(i.gross_total)"
            + ISSUED
            + " GROUP BY d",
        period.params());
  }

  private Map<LocalDate, BigDecimal> perDay(String sql, MapSqlParameterSource p) {
    var map = new HashMap<LocalDate, BigDecimal>();
    r.jdbc.query(
        sql,
        p,
        rs -> {
          map.put(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2).setScale(2));
        });
    return map;
  }

  private List<Amount> amounts(String sql, MapSqlParameterSource p) {
    return r.jdbc.query(
        sql,
        p,
        (rs, i) ->
            new Amount(
                rs.getString(1), rs.getBigDecimal(2).setScale(2), rs.getBigDecimal(3).longValue()));
  }

  private static BigDecimal zero() {
    return BigDecimal.ZERO.setScale(2);
  }
}
