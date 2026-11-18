package com.ngolahealth.backend.modules.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngolahealth.backend.common.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentRulesTest {

  private static Equipment monitor() {
    return new Equipment(
        new EquipmentDetails(
            "M1",
            "Monitor",
            EquipmentCategory.MONITORING,
            Criticality.MEDIUM,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            90,
            LocalDate.of(2026, 12, 1),
            365,
            LocalDate.of(2027, 1, 1),
            null));
  }

  @Test
  void maintenanceSetsNextDatesFromWhenItWasDone() {
    var e = monitor();
    var done = LocalDate.of(2026, 11, 17);
    e.applyMaintenance(MaintenanceType.PREVENTIVE, done, MaintenanceResult.PASSED, false);
    assertThat(e.getNextMaintenanceDate()).isEqualTo(done.plusDays(90));
    assertThat(e.getNextCalibrationDate()).isEqualTo(LocalDate.of(2027, 1, 1));
    e.applyMaintenance(MaintenanceType.CALIBRATION, done, MaintenanceResult.PASSED, false);
    assertThat(e.getNextCalibrationDate()).isEqualTo(done.plusDays(365));
  }

  @Test
  void statusFollowsFaultsAndResults() {
    var e = monitor();
    e.putOutOfService();
    assertThat(e.getStatus()).isEqualTo(EquipmentStatus.OUT_OF_SERVICE);
    e.applyMaintenance(MaintenanceType.CORRECTIVE, LocalDate.now(), MaintenanceResult.PASSED, true);
    assertThat(e.getStatus()).isEqualTo(EquipmentStatus.OUT_OF_SERVICE);
    e.applyMaintenance(
        MaintenanceType.CORRECTIVE, LocalDate.now(), MaintenanceResult.PASSED, false);
    assertThat(e.getStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
    e.applyMaintenance(
        MaintenanceType.INSPECTION, LocalDate.now(), MaintenanceResult.FAILED, false);
    assertThat(e.getStatus()).isEqualTo(EquipmentStatus.OUT_OF_SERVICE);
    e.retire("Fim de vida", UUID.randomUUID(), Instant.now());
    assertThatThrownBy(e::sendToMaintenance).isInstanceOf(BusinessException.class);
  }
}
