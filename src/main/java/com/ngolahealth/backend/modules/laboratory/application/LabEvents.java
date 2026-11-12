package com.ngolahealth.backend.modules.laboratory.application;

import java.util.UUID;

/**
 * Eventos publicados pelo laboratório depois de validar ou corrigir resultados. São entregues
 * depois do commit: quem os ouve (ex.: notificações) nunca impede nem desfaz o acto do laboratório.
 */
public final class LabEvents {

  private LabEvents() {}

  /** Identificação do pedido comum a todos os eventos. */
  public record RequestRef(
      UUID hospitalId,
      UUID requestId,
      String number,
      String patientLabel,
      UUID requestedBy,
      boolean urgent) {}

  /** Todos os exames do pedido foram validados. */
  public record ResultsReady(RequestRef request, int testCount, int abnormalCount) {}

  /** Um resultado alterado foi validado e o pedido ainda tem exames por concluir. */
  public record AbnormalResult(RequestRef request, String testName, String result) {}

  /** Um resultado já validado foi corrigido. */
  public record ResultAmended(
      RequestRef request, String testName, String before, String after, String reason) {}
}
