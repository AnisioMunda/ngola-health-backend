package com.ngolahealth.backend.modules.laboratory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngolahealth.backend.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResultFlagTest {

  private static LabTest test(ResultType type, String low, String high) {
    var t =
        new LabTest(
            new LabTestDetails(
                "HB",
                "Hemoglobina",
                TestCategory.HEMATOLOGY,
                SampleType.BLOOD,
                type,
                "g/dL",
                low == null ? null : new BigDecimal(low),
                high == null ? null : new BigDecimal(high),
                null,
                24));
    ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
    return t;
  }

  private static LabRequestItem item(LabTest t) {
    var r =
        new LabRequest(
            "LAB-1",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now(),
            LabPriority.ROUTINE,
            null,
            List.of(t));
    return r.getItems().getFirst();
  }

  @Test
  void numericFlagFollowsReferenceRangeCopiedAtRequestTime() {
    var item = item(test(ResultType.NUMERIC, "12", "16"));
    assertThat(item.flagFor(new BigDecimal("11.9"))).isEqualTo(ResultFlag.LOW);
    assertThat(item.flagFor(new BigDecimal("12"))).isEqualTo(ResultFlag.NORMAL);
    assertThat(item.flagFor(new BigDecimal("16"))).isEqualTo(ResultFlag.NORMAL);
    assertThat(item.flagFor(new BigDecimal("16.1"))).isEqualTo(ResultFlag.HIGH);
  }

  @Test
  void noRangeMeansNoFlagAndOneSidedRangesWork() {
    assertThat(item(test(ResultType.NUMERIC, null, null)).flagFor(BigDecimal.TEN)).isNull();
    assertThat(item(test(ResultType.NUMERIC, null, "5")).flagFor(BigDecimal.TEN))
        .isEqualTo(ResultFlag.HIGH);
  }

  @Test
  void qualitativeResultUsesTechnicianJudgementAndRequiresText() {
    var item = item(test(ResultType.QUALITATIVE, null, null));
    item.recordResult(
        new ResultValue(null, "Positivo (P. falciparum)", true, null),
        UUID.randomUUID(),
        Instant.now());
    assertThat(item.getFlag()).isEqualTo(ResultFlag.ABNORMAL);
    assertThatThrownBy(
            () ->
                item.recordResult(
                    new ResultValue(null, " ", false, null), UUID.randomUUID(), Instant.now()))
        .isInstanceOf(BusinessException.class);
  }
}
