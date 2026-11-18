package com.ngolahealth.backend.modules.equipment.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Dados editáveis de um equipamento, já normalizados. */
public record EquipmentDetails(
    String code,
    String name,
    EquipmentCategory category,
    Criticality criticality,
    String brand,
    String model,
    String serialNumber,
    UUID wardId,
    String location,
    LocalDate purchaseDate,
    BigDecimal purchasePrice,
    LocalDate warrantyUntil,
    Integer maintenanceIntervalDays,
    LocalDate nextMaintenanceDate,
    Integer calibrationIntervalDays,
    LocalDate nextCalibrationDate,
    String notes) {}
