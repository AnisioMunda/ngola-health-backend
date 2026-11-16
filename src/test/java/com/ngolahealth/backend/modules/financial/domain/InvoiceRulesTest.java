package com.ngolahealth.backend.modules.financial.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngolahealth.backend.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceRulesTest {

  private static Invoice draft(DocumentType type, String unitPrice, String vat, String reason) {
    return new Invoice(
        type,
        UUID.randomUUID(),
        null,
        new Customer("Ana", null, null),
        null,
        null,
        List.of(
            new LineInput(
                null,
                null,
                "Acto",
                BigDecimal.ONE,
                new BigDecimal(unitPrice),
                BigDecimal.ZERO,
                new BigDecimal(vat),
                reason)),
        UUID.randomUUID());
  }

  private static void issue(Invoice i) {
    i.issue(
        2026,
        1,
        "FT 2026/000001",
        new Issuer("H", "5417", "Luanda"),
        UUID.randomUUID(),
        Instant.now());
  }

  @Test
  void halfUpRounding() {
    assertThat(Money.percentOf(new BigDecimal("0.05"), new BigDecimal("50")))
        .isEqualByComparingTo("0.03");
    assertThat(Money.percentOf(new BigDecimal("333.33"), new BigDecimal("14")))
        .isEqualByComparingTo("46.67");
  }

  @Test
  void zeroVatLineNeedsExemptionReasonToIssue() {
    var i = draft(DocumentType.FT, "100", "0", null);
    assertThatThrownBy(() -> issue(i))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("motivo de isenção");
  }

  @Test
  void issuedDocumentIsFrozenAndPaymentsCannotExceedBalance() {
    var i = draft(DocumentType.FT, "100", "14", null);
    issue(i);
    assertThat(i.getGrossTotal()).isEqualByComparingTo("114.00");
    assertThatThrownBy(() -> i.edit(null, new Customer("X", null, null), null, null, List.of()))
        .isInstanceOf(BusinessException.class);
    i.registerPayment(new BigDecimal("14"));
    assertThat(i.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    assertThatThrownBy(() -> i.registerPayment(new BigDecimal("100.01")))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> i.cancel("x", UUID.randomUUID(), Instant.now()))
        .isInstanceOf(BusinessException.class);
    var nc = Invoice.creditNoteFor(i, "Erro", UUID.randomUUID());
    assertThat(nc.getGrossTotal()).isEqualByComparingTo(i.getGrossTotal());
    assertThat(nc.getRelatedInvoiceId()).isEqualTo(i.getId());
  }

  @Test
  void invoiceReceiptHasNoDueDate() {
    var i = draft(DocumentType.FR, "100", "14", null);
    assertThatThrownBy(
            () ->
                i.edit(
                    null,
                    new Customer("Ana", null, null),
                    java.time.LocalDate.now().plusDays(10),
                    null,
                    List.of()))
        .isInstanceOf(BusinessException.class);
  }
}
