package com.ngolahealth.backend.modules.equipment.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentResponse;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.MaintenanceRequest;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentFaultRepository;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentRepository;
import com.ngolahealth.backend.modules.equipment.domain.MaintenanceRecord;
import com.ngolahealth.backend.modules.equipment.domain.MaintenanceRecordRepository;
import com.ngolahealth.backend.modules.equipment.domain.MaintenanceResult;
import com.ngolahealth.backend.modules.equipment.domain.MaintenanceType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registo de manutenções: actualiza as próximas datas e o estado. Uma correctiva bem-sucedida
 * ligada a uma avaria resolve-a.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceService {

  private final EquipmentRepository equipment;
  private final EquipmentFaultRepository faults;
  private final MaintenanceRecordRepository records;
  private final EquipmentSupport support;
  private final AuditTrail audit;

  @Transactional
  public EquipmentResponse record(UUID equipmentId, MaintenanceRequest r) {
    var user = support.hospitalUser();
    var e = support.lock(equipmentId);
    e.requireNotRetired();
    if (r.performedOn().isAfter(support.today())) {
      throw new BadRequestException("FUTURE_DATE", "A manutenção não pode ter data futura.");
    }
    UUID resolvedFault = null;
    if (r.faultId() != null) {
      if (r.type() != MaintenanceType.CORRECTIVE) {
        throw new BadRequestException(
            "CORRECTIVE_REQUIRED", "Só uma manutenção correctiva resolve uma avaria.");
      }
      var f =
          faults
              .findForUpdate(r.faultId())
              .filter(x -> x.getEquipmentId().equals(e.getId()))
              .orElseThrow(
                  () ->
                      new NotFoundException(
                          "FAULT_NOT_FOUND", "A avaria não é deste equipamento."));
      if (r.result() == MaintenanceResult.PASSED) {
        f.resolve(r.description().strip(), support.now());
        faults.saveAndFlush(f);
        resolvedFault = f.getId();
      }
    }
    records.save(
        new MaintenanceRecord(
            e.getId(),
            r.faultId(),
            r.type(),
            r.performedOn(),
            r.performedBy().strip(),
            r.description().strip(),
            EquipmentSupport.optional(r.partsReplaced()),
            r.cost(),
            r.result(),
            user.id(),
            support.now()));
    e.applyMaintenance(
        r.type(),
        r.performedOn(),
        r.result(),
        support.blockingFaultsRemain(e.getId(), resolvedFault));
    equipment.saveAndFlush(e);
    audit.record(
        EquipmentService.entry(
            AuditAction.MAINTENANCE,
            e,
            user,
            r.type()
                + " ("
                + r.result()
                + ") em "
                + EquipmentSupport.label(e)
                + " por "
                + r.performedBy().strip()
                + "; estado: "
                + e.getStatus()));
    return support.toResponse(e);
  }
}
