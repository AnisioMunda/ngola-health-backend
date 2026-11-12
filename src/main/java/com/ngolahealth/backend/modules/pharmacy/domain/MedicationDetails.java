package com.ngolahealth.backend.modules.pharmacy.domain;

/** Dados editáveis de um medicamento, já normalizados. */
public record MedicationDetails(
    String name,
    String genericName,
    DosageForm dosageForm,
    String strength,
    String dispensingUnit,
    boolean requiresPrescription,
    int minStockLevel) {}
