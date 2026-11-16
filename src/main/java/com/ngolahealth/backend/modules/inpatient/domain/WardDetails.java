package com.ngolahealth.backend.modules.inpatient.domain;

/** Dados editáveis de uma enfermaria, já normalizados. */
public record WardDetails(
    String code,
    String name,
    WardType type,
    GenderPolicy genderPolicy,
    String floor,
    String notes) {}
