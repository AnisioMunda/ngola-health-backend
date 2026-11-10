package com.ngolahealth.backend.modules.users.domain;

/** Dados pessoais e profissionais editáveis de um utilizador, já normalizados. */
public record UserProfile(
    String fullName, String email, String phone, String specialty, String professionalCardNumber) {}
