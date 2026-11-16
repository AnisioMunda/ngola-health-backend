package com.ngolahealth.backend.modules.inpatient.application;

import java.util.UUID;

/** Eventos do internamento, entregues depois do commit. */
public final class AdmissionEvents {

  private AdmissionEvents() {}

  /** Um médico ficou responsável por um internamento (avisado se não foi ele a decidir). */
  public record DoctorAssigned(
      UUID hospitalId,
      UUID admissionId,
      UUID doctorId,
      UUID actorId,
      String patientLabel,
      String location,
      boolean newAdmission) {}
}
