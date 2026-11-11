package com.ngolahealth.backend.modules.patients.domain;

import java.time.LocalDate;

/** Dados editáveis de um paciente, já normalizados (sem espaços, opcionais vazios a null). */
public record PatientDetails(
    String fullName,
    LocalDate birthDate,
    Gender gender,
    String nationalId,
    String healthCardNumber,
    String phone,
    String email,
    String address,
    String province,
    String municipality,
    String emergencyContactName,
    String emergencyContactPhone,
    String emergencyContactRelationship,
    String bloodType,
    String allergies,
    String chronicConditions,
    String notes) {}
