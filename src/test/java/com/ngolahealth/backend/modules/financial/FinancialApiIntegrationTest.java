package com.ngolahealth.backend.modules.financial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.support.AuthFixtures;
import com.ngolahealth.backend.support.TenantScenario;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Preços, cálculo de totais (HALF_UP linha a linha), emissão com numeração sem falhas, pagamentos e
 * recibos, factura-recibo, anulação, nota de crédito, caixa e permissões.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinancialApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;

  private TenantScenario scenario;
  private UUID hospital;
  private RequestPostProcessor finance;
  private RequestPostProcessor cashier;
  private RequestPostProcessor nurse;
  private String patient;
  private String consult;
  private String bloodCount;

  @BeforeEach
  void setUp() throws Exception {
    scenario = new TenantScenario(fixtures, mockMvc, jdbc);
    hospital = scenario.hospital("FN");
    jdbc.update("UPDATE hospitals SET tax_id = '5417000000' WHERE id = ?", hospital.toString());
    finance = scenario.as("fin", hospital, Role.FINANCIAL);
    cashier = scenario.as("rec", hospital, Role.RECEPTIONIST);
    nurse = scenario.as("enf", hospital, Role.NURSE);
    patient =
        id(
            perform(
                json(
                    post("/patients"),
                    "{\"fullName\":\"Ana Silva\",\"birthDate\":\"1990-05-01\",\"gender\":\"FEMALE\"}"),
                cashier));
    consult =
        id(
            perform(
                json(
                    post("/financial/prices"),
                    price(
                        "CONS",
                        "Consulta geral",
                        "5000",
                        "0",
                        "\"vatExemptionReason\":\"Isento — serviços de saúde\"")),
                finance));
    bloodCount =
        id(
            perform(
                json(post("/financial/prices"), price("HEMO", "Hemograma", "2500.50", "14", null)),
                finance));
  }

  @AfterEach
  void cleanUp() {
    scenario.cleanUp();
  }

  @Test
  void totalsAreComputedLineByLineWithHalfUp() throws Exception {
    perform(json(post("/financial/prices"), price("X", "Sem motivo", "10", "0", null)), finance)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAT_EXEMPTION_REQUIRED"));
    perform(json(post("/financial/invoices"), invoiceBody("FT", standardLines())), cashier)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.number").doesNotExist())
        .andExpect(jsonPath("$.currency").value("AOA"))
        .andExpect(jsonPath("$.customerName").value("Ana Silva"))
        .andExpect(jsonPath("$.items[1].discountAmount").value(750.15))
        .andExpect(jsonPath("$.items[1].netAmount").value(6751.35))
        .andExpect(jsonPath("$.items[1].vatAmount").value(945.19))
        .andExpect(jsonPath("$.items[2].vatAmount").value(46.67))
        .andExpect(jsonPath("$.netTotal").value(12084.68))
        .andExpect(jsonPath("$.vatTotal").value(991.86))
        .andExpect(jsonPath("$.grossTotal").value(13076.54));
    // O preço vem sempre da tabela, mesmo que o pedido traga outro.
    perform(
            json(
                post("/financial/invoices"),
                invoiceBody(
                    "FT",
                    "{\"servicePriceId\":\"" + consult + "\",\"quantity\":1,\"unitPrice\":1}")),
            cashier)
        .andExpect(jsonPath("$.items[0].unitPrice").value(5000.0));
  }

  @Test
  void issueNumbersPaymentsReceiptsAndCreditNote() throws Exception {
    String ft = draft("FT");
    String number =
        JsonPath.read(
            perform(post("/financial/invoices/" + ft + "/issue"), cashier)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.issuerTaxId").value("5417000000"))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.number");
    assertThat(number).matches("FT \\d{4}/000001");
    perform(post("/financial/invoices/" + draft("FT") + "/issue"), cashier)
        .andExpect(jsonPath("$.number").value(Matchers.endsWith("/000002")));

    perform(json(put("/financial/invoices/" + ft), invoiceBody("FT", standardLines())), cashier)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCUMENT_ISSUED"));
    perform(
            json(
                post("/financial/invoices/" + ft + "/payments"),
                "{\"amount\":5000,\"method\":\"CASH\"}"),
            cashier)
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
        .andExpect(jsonPath("$.balance").value(8076.54))
        .andExpect(
            jsonPath("$.payments[0].receiptNumber")
                .value(Matchers.matchesPattern("RC \\d{4}/000001")));
    perform(
            json(
                post("/financial/invoices/" + ft + "/payments"),
                "{\"amount\":9000,\"method\":\"CARD\"}"),
            cashier)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AMOUNT_EXCEEDS_BALANCE"));
    perform(
            json(
                post("/financial/invoices/" + ft + "/payments"),
                "{\"amount\":8076.54,\"method\":\"MULTICAIXA_EXPRESS\",\"reference\":\"MCX123\"}"),
            cashier)
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.balance").value(0.0));

    perform(json(post("/financial/invoices/" + ft + "/cancel"), "{\"reason\":\"x\"}"), finance)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOT_CANCELLABLE"));
    perform(
            json(post("/financial/invoices/" + ft + "/credit-note"), "{\"reason\":\"Erro\"}"),
            cashier)
        .andExpect(status().isForbidden());
    perform(
            json(
                post("/financial/invoices/" + ft + "/credit-note"),
                "{\"reason\":\"Serviço não prestado\"}"),
            finance)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.documentType").value("NC"))
        .andExpect(jsonPath("$.number").value(Matchers.matchesPattern("NC \\d{4}/000001")))
        .andExpect(jsonPath("$.relatedInvoiceNumber").value(number))
        .andExpect(jsonPath("$.grossTotal").value(13076.54));
    perform(get("/financial/invoices/" + ft), finance)
        .andExpect(jsonPath("$.status").value("CREDITED"))
        .andExpect(jsonPath("$.creditNoteNumber").value(Matchers.startsWith("NC ")));

    perform(get("/financial/cash"), finance)
        .andExpect(jsonPath("$.total").value(13076.54))
        .andExpect(jsonPath("$.receipts").value(2))
        .andExpect(jsonPath("$.byMethod.CASH").value(5000.0));
  }

  @Test
  void invoiceReceiptIsPaidOnIssue() throws Exception {
    String fr = draft("FR");
    perform(post("/financial/invoices/" + fr + "/issue"), cashier)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_REQUIRED"));
    perform(
            json(
                post("/financial/invoices/" + fr + "/issue"),
                "{\"paymentMethod\":\"CARD\",\"reference\":\"TPA 991\"}"),
            cashier)
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.number").value(Matchers.matchesPattern("FR \\d{4}/000001")))
        .andExpect(jsonPath("$.payments[0].receiptNumber").value(Matchers.startsWith("FR ")));
    perform(
            json(
                post("/financial/invoices/" + fr + "/payments"),
                "{\"amount\":1,\"method\":\"CASH\"}"),
            cashier)
        .andExpect(status().isConflict());
  }

  @Test
  void cancelDraftsAndPermissions() throws Exception {
    String ft = draft("FT");
    perform(post("/financial/invoices/" + ft + "/issue"), cashier);
    perform(delete("/financial/invoices/" + ft), cashier)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCUMENT_ISSUED"));
    perform(
            json(post("/financial/invoices/" + ft + "/cancel"), "{\"reason\":\"Duplicado\"}"),
            finance)
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.cancellationReason").value("Duplicado"));
    String other = draft("FT");
    perform(delete("/financial/invoices/" + other), cashier).andExpect(status().isNoContent());

    perform(get("/financial/invoices"), nurse).andExpect(status().isForbidden());
    perform(json(post("/financial/prices"), price("Y", "Y", "1", "14", null)), cashier)
        .andExpect(status().isForbidden());
    perform(get("/financial/invoices").param("status", "CANCELLED"), cashier)
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void numberingHasNoGapsWhenAnIssueFailsOrRunsConcurrently() throws Exception {
    // Uma emissão que falha (hospital sem NIF) não consome número.
    jdbc.update("UPDATE hospitals SET tax_id = NULL WHERE id = ?", hospital.toString());
    String first = draft("FT");
    perform(post("/financial/invoices/" + first + "/issue"), cashier)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("HOSPITAL_TAX_ID_MISSING"));
    jdbc.update("UPDATE hospitals SET tax_id = '5417000000' WHERE id = ?", hospital.toString());

    var drafts = List.of(first, draft("FT"), draft("FT"), draft("FT"));
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(drafts.size());
    List<Callable<Integer>> tasks = new ArrayList<>();
    for (var d : drafts) {
      tasks.add(
          () -> {
            start.await();
            return perform(post("/financial/invoices/" + d + "/issue"), cashier)
                .andReturn()
                .getResponse()
                .getStatus();
          });
    }
    var futures = tasks.stream().map(pool::submit).toList();
    start.countDown();
    for (var f : futures) {
      assertThat(f.get()).isEqualTo(200);
    }
    pool.shutdown();
    assertThat(
            jdbc.queryForList(
                "SELECT sequence_number FROM invoices WHERE hospital_id = ? AND document_type = 'FT'"
                    + " ORDER BY sequence_number",
                Long.class,
                hospital.toString()))
        .containsExactly(1L, 2L, 3L, 4L);
  }

  // ---------- Auxiliares ----------

  private String draft(String type) throws Exception {
    return id(
        perform(json(post("/financial/invoices"), invoiceBody(type, standardLines())), cashier));
  }

  private String standardLines() {
    return """
        {"servicePriceId":"%s","quantity":1},
        {"servicePriceId":"%s","quantity":3,"discountPercent":10},
        {"description":"Material de penso","quantity":1,"unitPrice":333.33,"vatRate":14}"""
        .formatted(consult, bloodCount);
  }

  private String invoiceBody(String type, String lines) {
    return """
        {"documentType":"%s","patientId":"%s","lines":[%s]}"""
        .formatted(type, patient, lines);
  }

  private static String price(
      String code, String description, String price, String vat, String extra) {
    return """
        {"code":"%s","description":"%s","category":"CONSULTATION","unitPrice":%s,"vatRate":%s%s}"""
        .formatted(code, description, price, vat, extra == null ? "" : "," + extra);
  }

  private static String id(ResultActions result) throws Exception {
    var r = result.andReturn().getResponse();
    assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
    return JsonPath.read(r.getContentAsString(), "$.id");
  }

  private ResultActions perform(MockHttpServletRequestBuilder request, RequestPostProcessor as)
      throws Exception {
    return mockMvc.perform(request.with(as));
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder builder, String content) {
    return builder.contentType(MediaType.APPLICATION_JSON).content(content);
  }
}
