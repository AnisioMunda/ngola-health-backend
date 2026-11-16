package com.ngolahealth.backend.modules.notifications.application;

import com.ngolahealth.backend.modules.inpatient.application.AdmissionEvents;
import com.ngolahealth.backend.modules.notifications.domain.NotificationContent;
import com.ngolahealth.backend.modules.notifications.domain.NotificationPriority;
import com.ngolahealth.backend.modules.notifications.domain.NotificationType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Avisa o médico que ficou responsável por um internamento decidido por outra pessoa. */
@Slf4j
@Component
@RequiredArgsConstructor
public class InpatientNotificationListener {

  private final NotificationSender sender;

  @TransactionalEventListener
  public void onDoctorAssigned(AdmissionEvents.DoctorAssigned e) {
    if (e.doctorId().equals(e.actorId())) {
      return;
    }
    try {
      sender.send(
          e.hospitalId(),
          List.of(e.doctorId()),
          new NotificationContent(
              NotificationType.INPATIENT_ASSIGNED,
              NotificationPriority.HIGH,
              (e.newAdmission() ? "Novo internamento a seu cargo" : "Internamento passou para si")
                  + " — "
                  + e.location(),
              e.patientLabel() + ".",
              "/inpatient/admissions/" + e.admissionId(),
              "ADMISSION",
              e.admissionId(),
              null));
    } catch (RuntimeException ex) {
      log.error("Falha ao notificar internamento {}", e.admissionId(), ex);
    }
  }
}
