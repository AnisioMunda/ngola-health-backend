package com.ngolahealth.backend.modules.hospitals.domain;

/** Dados descritivos de um hospital, já normalizados (sem espaços, opcionais vazios a null). */
public record HospitalDetails(
    String name,
    String code,
    HospitalType type,
    String province,
    String municipality,
    String address,
    String phone,
    String email,
    String taxId) {}
