package com.ngolahealth.backend.modules.equipment.api;

import com.ngolahealth.backend.modules.equipment.domain.Criticality;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentCategory;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentStatus;
import com.ngolahealth.backend.modules.equipment.domain.FaultStatus;
import com.ngolahealth.backend.modules.equipment.domain.MaintenanceResult;
import com.ngolahealth.backend.modules.equipment.domain.MaintenanceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EquipmentDtos {

  private EquipmentDtos() {}

  public record EquipmentRequest(
      @NotBlank(message = "Indique o código (n.º de inventário).")
          @Size(max = 50, message = "O código não pode exceder 50 caracteres.")
          @Pattern(regexp = "[A-Za-z0-9._/-]+", message = "Use só letras, números, ., /, - e _.")
          String code,
      @NotBlank(message = "Indique o nome.")
          @Size(max = 200, message = "O nome não pode exceder 200 caracteres.")
          String name,
      @NotNull(message = "Seleccione a categoria.") EquipmentCategory category,
      @NotNull(message = "Indique a criticidade.") Criticality criticality,
      @Size(max = 100, message = "A marca não pode exceder 100 caracteres.") String brand,
      @Size(max = 100, message = "O modelo não pode exceder 100 caracteres.") String model,
      @Size(max = 100, message = "O n.º de série não pode exceder 100 caracteres.")
          String serialNumber,
      UUID wardId,
      @Size(max = 200, message = "A localização não pode exceder 200 caracteres.") String location,
      LocalDate purchaseDate,
      @DecimalMin(value = "0.00", message = "O valor não pode ser negativo.")
          @Digits(integer = 12, fraction = 2, message = "Valor inválido.")
          BigDecimal purchasePrice,
      LocalDate warrantyUntil,
      @Min(value = 1, message = "Periodicidade inválida.")
          @Max(value = 3650, message = "Periodicidade inválida (máx. 10 anos).")
          Integer maintenanceIntervalDays,
      LocalDate nextMaintenanceDate,
      @Min(value = 1, message = "Periodicidade inválida.")
          @Max(value = 3650, message = "Periodicidade inválida (máx. 10 anos).")
          Integer calibrationIntervalDays,
      LocalDate nextCalibrationDate,
      @Size(max = 1000, message = "As notas não podem exceder 1000 caracteres.") String notes,
      Long version) {}

  public record FaultRequest(
      @NotBlank(message = "Descreva o problema.")
          @Size(max = 1000, message = "A descrição não pode exceder 1000 caracteres.")
          String description,
      Boolean outOfService) {}

  public record MaintenanceRequest(
      @NotNull(message = "Seleccione o tipo.") MaintenanceType type,
      @NotNull(message = "Indique a data.") LocalDate performedOn,
      @NotBlank(message = "Indique quem fez o trabalho.")
          @Size(max = 200, message = "Máximo de 200 caracteres.")
          String performedBy,
      @NotBlank(message = "Descreva o trabalho feito.")
          @Size(max = 2000, message = "A descrição não pode exceder 2000 caracteres.")
          String description,
      @Size(max = 500, message = "Máximo de 500 caracteres.") String partsReplaced,
      @DecimalMin(value = "0.00", message = "O custo não pode ser negativo.")
          @Digits(integer = 12, fraction = 2, message = "Custo inválido.")
          BigDecimal cost,
      @NotNull(message = "Indique o resultado.") MaintenanceResult result,
      UUID faultId) {}

  public record ReasonRequest(
      @NotBlank(message = "Indique o motivo.")
          @Size(max = 300, message = "O motivo não pode exceder 300 caracteres.")
          String reason) {}

  public record FaultResponse(
      UUID id,
      UUID equipmentId,
      String equipmentName,
      String equipmentCode,
      Criticality criticality,
      String description,
      boolean outOfService,
      FaultStatus status,
      String reportedByName,
      Instant reportedAt,
      Instant resolvedAt,
      String resolution) {}

  public record MaintenanceResponse(
      UUID id,
      UUID faultId,
      MaintenanceType type,
      LocalDate performedOn,
      String performedBy,
      String description,
      String partsReplaced,
      BigDecimal cost,
      MaintenanceResult result,
      String recordedByName,
      Instant recordedAt) {}

  public record EquipmentSummary(
      UUID id,
      String code,
      String name,
      EquipmentCategory category,
      Criticality criticality,
      EquipmentStatus status,
      String wardName,
      String location,
      LocalDate nextMaintenanceDate,
      LocalDate nextCalibrationDate,
      boolean maintenanceOverdue,
      boolean calibrationOverdue,
      int openFaults) {}

  public record EquipmentResponse(
      UUID id,
      String code,
      String name,
      EquipmentCategory category,
      Criticality criticality,
      String brand,
      String model,
      String serialNumber,
      UUID wardId,
      String wardName,
      String location,
      EquipmentStatus status,
      LocalDate purchaseDate,
      BigDecimal purchasePrice,
      LocalDate warrantyUntil,
      boolean underWarranty,
      Integer maintenanceIntervalDays,
      LocalDate nextMaintenanceDate,
      boolean maintenanceOverdue,
      Integer calibrationIntervalDays,
      LocalDate nextCalibrationDate,
      boolean calibrationOverdue,
      String notes,
      Instant retiredAt,
      String retiredByName,
      String retirementReason,
      List<FaultResponse> faults,
      List<MaintenanceResponse> maintenance,
      BigDecimal maintenanceCostTotal,
      long version) {}

  public record EquipmentOverview(
      long operational,
      long outOfService,
      long underMaintenance,
      long retired,
      int openFaults,
      int dueIn30Days) {}
}
