package com.ngolahealth.backend.modules.financial.domain;

/** Cliente fiscal do documento. Sem NIF = consumidor final. */
public record Customer(String name, String taxId, String address) {}
