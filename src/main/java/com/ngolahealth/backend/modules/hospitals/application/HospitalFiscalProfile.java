package com.ngolahealth.backend.modules.hospitals.application;

/** Dados do hospital que aparecem como emitente nos documentos de facturação. */
public record HospitalFiscalProfile(String name, String taxId, String address) {}
