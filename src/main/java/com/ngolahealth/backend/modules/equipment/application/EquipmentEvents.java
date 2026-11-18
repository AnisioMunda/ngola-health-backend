package com.ngolahealth.backend.modules.equipment.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EquipmentEvents {

  private EquipmentEvents() {}

  public record FaultReported(
      UUID hospitalId,
      UUID equipmentId,
      String equipmentLabel,
      boolean critical,
      boolean outOfService,
      String description,
      UUID reportedBy) {}

  /** Resumo diário: equipamentos com manutenção ou calibração vencida ou a vencer. */
  public record MaintenanceDue(
      UUID hospitalId, LocalDate date, int overdue, int dueSoon, List<String> names) {}
}
