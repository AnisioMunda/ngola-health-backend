package com.ngolahealth.backend.modules.equipment.application;

import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentResponse;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.FaultRequest;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.FaultResponse;
import com.ngolahealth.backend.modules.equipment.domain.Criticality;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentFault;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentFaultRepository;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Avarias: qualquer profissional reporta; um gestor descarta (não era avaria). */
@Service
@RequiredArgsConstructor
public class FaultService {

  private final EquipmentRepository equipment;
  private final EquipmentFaultRepository faults;
  private final EquipmentSupport support;
  private final AuditTrail audit;
  private final ApplicationEventPublisher events;

  @Transactional(readOnly = true)
  public List<FaultResponse> open() {
    support.hospitalUser();
    return support.toFaults(faults.open());
  }

  /** Um equipamento crítico com avaria fica sempre fora de serviço. */
  @Transactional
  public EquipmentResponse report(UUID equipmentId, FaultRequest r) {
    var user = support.hospitalUser();
    var e = support.lock(equipmentId);
    e.requireNotRetired();
    boolean critical = e.getCriticality() == Criticality.HIGH;
    boolean outOfService = critical || Boolean.TRUE.equals(r.outOfService());
    faults.save(
        new EquipmentFault(
            e.getId(), r.description().strip(), outOfService, user.id(), support.now()));
    if (outOfService) {
      e.putOutOfService();
    }
    equipment.saveAndFlush(e);
    audit.record(
        EquipmentService.entry(
            AuditAction.FAULT_REPORTED,
            e,
            user,
            "Avaria reportada em "
                + EquipmentSupport.label(e)
                + (outOfService ? " (fora de serviço)" : "")
                + ": "
                + r.description().strip()));
    events.publishEvent(
        new EquipmentEvents.FaultReported(
            user.hospitalId(),
            e.getId(),
            EquipmentSupport.label(e),
            critical,
            outOfService,
            r.description().strip(),
            user.id()));
    return support.toResponse(e);
  }

  @Transactional
  public EquipmentResponse dismiss(UUID faultId, String reason) {
    var user = support.hospitalUser();
    var f =
        faults
            .findForUpdate(faultId)
            .orElseThrow(() -> new NotFoundException("FAULT_NOT_FOUND", "Avaria não encontrada."));
    var e = support.lock(f.getEquipmentId());
    f.dismiss(reason.strip(), support.now());
    faults.saveAndFlush(f);
    if (!support.blockingFaultsRemain(e.getId(), f.getId())) {
      e.backInService();
      equipment.saveAndFlush(e);
    }
    audit.record(
        EquipmentService.entry(
            AuditAction.UPDATE,
            e,
            user,
            "Avaria descartada em " + EquipmentSupport.label(e) + " (" + reason.strip() + ")"));
    return support.toResponse(e);
  }
}
