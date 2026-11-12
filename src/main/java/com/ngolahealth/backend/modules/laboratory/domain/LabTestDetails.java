package com.ngolahealth.backend.modules.laboratory.domain;

import java.math.BigDecimal;

public record LabTestDetails(
    String code,
    String name,
    TestCategory category,
    SampleType sampleType,
    ResultType resultType,
    String unit,
    BigDecimal referenceLow,
    BigDecimal referenceHigh,
    String referenceText,
    int turnaroundHours) {}
