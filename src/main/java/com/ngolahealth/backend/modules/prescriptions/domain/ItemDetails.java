package com.ngolahealth.backend.modules.prescriptions.domain;

import java.util.UUID;

/** Dados de uma linha, já validados e normalizados. */
public record ItemDetails(
    UUID medicationId,
    int quantity,
    String dosage,
    String frequency,
    Integer durationDays,
    Route route,
    String instructions) {}
