package com.ngolahealth.backend.modules.notifications.application;

import com.ngolahealth.backend.modules.laboratory.application.LabEvents;
import com.ngolahealth.backend.modules.notifications.domain.NotificationContent;
import com.ngolahealth.backend.modules.notifications.domain.NotificationPriority;
import com.ngolahealth.backend.modules.notifications.domain.NotificationType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisa o médico que pediu os exames. Corre depois do commit do laboratório: uma falha aqui fica no
 * log e nunca desfaz a validação.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LabNotificationListener {

  static final String REFERENCE_TYPE = "LAB_REQUEST";

  private final NotificationSender sender;

  @TransactionalEventListener
  public void onResultsReady(LabEvents.ResultsReady e) {
    var r = e.request();
    boolean abnormal = e.abnormalCount() > 0;
    deliver(
        r,
        new NotificationContent(
            NotificationType.LAB_RESULTS_READY,
            abnormal || r.urgent() ? NotificationPriority.HIGH : NotificationPriority.NORMAL,
            "Resultados disponíveis — " + r.number(),
            r.patientLabel()
                + ": "
                + e.testCount()
                + (e.testCount() == 1 ? " exame validado" : " exames validados")
                + (abnormal
                    ? ", "
                        + e.abnormalCount()
                        + (e.abnormalCount() == 1 ? " alterado." : " alterados.")
                    : ", todos dentro da referência."),
            link(r),
            REFERENCE_TYPE,
            r.requestId(),
            "LAB_RESULTS_READY:" + r.requestId()));
  }

  @TransactionalEventListener
  public void onAbnormalResult(LabEvents.AbnormalResult e) {
    var r = e.request();
    deliver(
        r,
        new NotificationContent(
            NotificationType.LAB_ABNORMAL_RESULT,
            NotificationPriority.HIGH,
            "Resultado alterado — " + e.testName(),
            r.patientLabel()
                + ": "
                + e.testName()
                + " = "
                + e.result()
                + ". O pedido "
                + r.number()
                + " ainda tem exames por concluir.",
            link(r),
            REFERENCE_TYPE,
            r.requestId(),
            null));
  }

  @TransactionalEventListener
  public void onResultAmended(LabEvents.ResultAmended e) {
    var r = e.request();
    deliver(
        r,
        new NotificationContent(
            NotificationType.LAB_RESULT_AMENDED,
            NotificationPriority.HIGH,
            "Resultado corrigido — " + e.testName() + " (" + r.number() + ")",
            r.patientLabel() + ": " + e.before() + " → " + e.after() + ". Motivo: " + e.reason(),
            link(r),
            REFERENCE_TYPE,
            r.requestId(),
            null));
  }

  private void deliver(LabEvents.RequestRef r, NotificationContent content) {
    try {
      sender.send(r.hospitalId(), List.of(r.requestedBy()), content);
    } catch (RuntimeException ex) {
      log.error("Falha ao notificar {} do pedido {}", content.type(), r.number(), ex);
    }
  }

  private static String link(LabEvents.RequestRef r) {
    return "/lab/requests/" + r.requestId();
  }
}
