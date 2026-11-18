package com.ngolahealth.backend.modules.equipment.application;

import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentResponse;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentSummary;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.FaultResponse;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.MaintenanceResponse;
import com.ngolahealth.backend.modules.equipment.domain.Equipment;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentFault;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentFaultRepository;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentRepository;
import com.ngolahealth.backend.modules.equipment.domain.FaultStatus;
import com.ngolahealth.backend.modules.equipment.domain.MaintenanceRecord;
import com.ngolahealth.backend.modules.equipment.domain.MaintenanceRecordRepository;
import com.ngolahealth.backend.modules.inpatient.application.WardDirectory;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EquipmentSupport {

  private final EquipmentRepository equipment;
  private final EquipmentFaultRepository faults;
  private final MaintenanceRecordRepository maintenance;
  private final WardDirectory wards;
  private final StaffDirectory staff;
  private final CurrentUserProvider currentUser;
  private final AppProperties app;
  private final Clock clock;

  CurrentUser hospitalUser() {
    var user = currentUser.get();
    if (user.hospitalId() == null) {
      throw new ForbiddenException(
          "HOSPITAL_SCOPE_REQUIRED",
          "Os equipamentos só estão acessíveis a utilizadores de um hospital.");
    }
    return user;
  }

  Instant now() {
    return clock.instant();
  }

  LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), app.timeZone());
  }

  Equipment lock(UUID id) {
    return equipment
        .findForUpdate(id)
        .orElseThrow(
            () -> new NotFoundException("EQUIPMENT_NOT_FOUND", "Equipamento não encontrado."));
  }

  Equipment load(UUID id) {
    return equipment
        .findScoped(id)
        .orElseThrow(
            () -> new NotFoundException("EQUIPMENT_NOT_FOUND", "Equipamento não encontrado."));
  }

  static boolean overdue(LocalDate date, LocalDate today) {
    return date != null && date.isBefore(today);
  }

  static String label(Equipment e) {
    return e.getName() + " (" + e.getCode() + ")";
  }

  List<EquipmentSummary> toSummaries(List<Equipment> list) {
    var wardNames =
        wards.names(
            list.stream().map(Equipment::getWardId).filter(Objects::nonNull).distinct().toList());
    var openByEquipment =
        faults.open().stream()
            .collect(Collectors.groupingBy(EquipmentFault::getEquipmentId, Collectors.counting()));
    var today = today();
    return list.stream()
        .map(
            e ->
                new EquipmentSummary(
                    e.getId(),
                    e.getCode(),
                    e.getName(),
                    e.getCategory(),
                    e.getCriticality(),
                    e.getStatus(),
                    e.getWardId() == null ? null : wardNames.get(e.getWardId()),
                    e.getLocation(),
                    e.getNextMaintenanceDate(),
                    e.getNextCalibrationDate(),
                    overdue(e.getNextMaintenanceDate(), today),
                    overdue(e.getNextCalibrationDate(), today),
                    openByEquipment.getOrDefault(e.getId(), 0L).intValue()))
        .toList();
  }

  EquipmentResponse toResponse(Equipment e) {
    var faultList = faults.forEquipment(e.getId());
    var records = maintenance.forEquipment(e.getId());
    var names =
        staff.names(
            Stream.concat(
                    Stream.concat(
                        faultList.stream().map(EquipmentFault::getReportedBy),
                        records.stream().map(MaintenanceRecord::getRecordedBy)),
                    Stream.of(e.getRetiredBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    var today = today();
    var wardName =
        e.getWardId() == null ? null : wards.names(List.of(e.getWardId())).get(e.getWardId());
    return new EquipmentResponse(
        e.getId(),
        e.getCode(),
        e.getName(),
        e.getCategory(),
        e.getCriticality(),
        e.getBrand(),
        e.getModel(),
        e.getSerialNumber(),
        e.getWardId(),
        wardName,
        e.getLocation(),
        e.getStatus(),
        e.getPurchaseDate(),
        e.getPurchasePrice(),
        e.getWarrantyUntil(),
        e.getWarrantyUntil() != null && !e.getWarrantyUntil().isBefore(today),
        e.getMaintenanceIntervalDays(),
        e.getNextMaintenanceDate(),
        overdue(e.getNextMaintenanceDate(), today),
        e.getCalibrationIntervalDays(),
        e.getNextCalibrationDate(),
        overdue(e.getNextCalibrationDate(), today),
        e.getNotes(),
        e.getRetiredAt(),
        e.getRetiredBy() == null ? null : names.get(e.getRetiredBy()),
        e.getRetirementReason(),
        toFaults(faultList, Map.of(e.getId(), e), names),
        records.stream()
            .map(
                m ->
                    new MaintenanceResponse(
                        m.getId(),
                        m.getFaultId(),
                        m.getType(),
                        m.getPerformedOn(),
                        m.getPerformedBy(),
                        m.getDescription(),
                        m.getPartsReplaced(),
                        m.getCost(),
                        m.getResult(),
                        names.get(m.getRecordedBy()),
                        m.getRecordedAt()))
            .toList(),
        records.stream()
            .map(MaintenanceRecord::getCost)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2),
        e.getVersion() == null ? 0 : e.getVersion());
  }

  List<FaultResponse> toFaults(Collection<EquipmentFault> list) {
    var ids = list.stream().map(EquipmentFault::getEquipmentId).distinct().toList();
    Map<UUID, Equipment> byId =
        ids.isEmpty()
            ? java.util.Collections.emptyMap()
            : equipment.findAllById(ids).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity()));
    var names =
        staff.names(list.stream().map(EquipmentFault::getReportedBy).collect(Collectors.toSet()));
    return toFaults(list, byId, names);
  }

  private static List<FaultResponse> toFaults(
      Collection<EquipmentFault> list, Map<UUID, Equipment> byId, Map<UUID, String> names) {
    return list.stream()
        .map(
            f -> {
              var e = byId.get(f.getEquipmentId());
              return new FaultResponse(
                  f.getId(),
                  f.getEquipmentId(),
                  e == null ? null : e.getName(),
                  e == null ? null : e.getCode(),
                  e == null ? null : e.getCriticality(),
                  f.getDescription(),
                  f.isOutOfService(),
                  f.getStatus(),
                  names.get(f.getReportedBy()),
                  f.getReportedAt(),
                  f.getResolvedAt(),
                  f.getResolution());
            })
        .toList();
  }

  /** Há outra avaria aberta, que impeça o uso, além da indicada? */
  boolean blockingFaultsRemain(UUID equipmentId, UUID except) {
    return faults.forEquipment(equipmentId).stream()
        .anyMatch(
            f ->
                f.getStatus() == FaultStatus.OPEN
                    && f.isOutOfService()
                    && !f.getId().equals(except));
  }

  static String optional(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }
}
