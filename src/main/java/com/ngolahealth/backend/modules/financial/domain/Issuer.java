package com.ngolahealth.backend.modules.financial.domain;

/** Dados do hospital emitente, copiados para o documento na emissão. */
public record Issuer(String name, String taxId, String address) {}
