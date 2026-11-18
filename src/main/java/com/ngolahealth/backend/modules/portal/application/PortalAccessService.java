package com.ngolahealth.backend.modules.portal.application;

import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.IssuedCode;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.PortalStatus;
import com.ngolahealth.backend.modules.portal.domain.ActivationCode;
import com.ngolahealth.backend.modules.portal.domain.ActivationCodeRepository;
import com.ngolahealth.backend.modules.users.application.PortalAccounts;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acesso de um doente ao portal, gerido pelo pessoal: estado, código de activação (entregue em mão,
 * com a identidade verificada na recepção) e desactivação.
 */
@Service
@RequiredArgsConstructor
public class PortalAccessService {

  static final Duration CODE_VALIDITY = Duration.ofHours(72);

  private final ActivationCodeRepository codes;
  private final PortalAccounts accounts;
  private final PatientDirectory patients;
  private final CurrentUserProvider currentUser;
  private final AuditTrail audit;
  private final Clock clock;

  @Transactional(readOnly = true)
  public PortalStatus status(UUID patientId) {
    hospitalUser();
    requirePatient(patientId);
    var account = accounts.find(patientId);
    var pending =
        codes.pendingFor(patientId).stream().filter(c -> c.isUsable(clock.instant())).findFirst();
    return new PortalStatus(
        account.isPresent(),
        account.map(PortalAccounts.AccountInfo::active).orElse(false),
        account.map(PortalAccounts.AccountInfo::username).orElse(null),
        account.map(PortalAccounts.AccountInfo::email).orElse(null),
        account.map(PortalAccounts.AccountInfo::lastLoginAt).orElse(null),
        pending.map(ActivationCode::getExpiresAt).orElse(null));
  }

  /** Gera um código novo (os anteriores por usar deixam de servir). Mostrado uma única vez. */
  @Transactional
  public IssuedCode issueCode(UUID patientId) {
    var user = hospitalUser();
    var patient = requirePatient(patientId);
    if (!patient.active()) {
      throw new ConflictException("PATIENT_INACTIVE", "O doente está inactivo.");
    }
    if (accounts.find(patientId).isPresent()) {
      throw new ConflictException("PORTAL_ACCOUNT_EXISTS", "Este doente já tem conta no portal.");
    }
    var now = clock.instant();
    codes.pendingFor(patientId).forEach(c -> c.revoke(now));
    var code = ActivationCodes.generate();
    var expires = now.plus(CODE_VALIDITY);
    codes.saveAndFlush(
        new ActivationCode(
            user.hospitalId(), patientId, ActivationCodes.hash(code), expires, user.id(), now));
    audit.record(
        AuditEntry.success(
            AuditAction.PORTAL_CODE_ISSUED,
            AuditEntityType.PATIENT,
            patientId,
            user.hospitalId(),
            "Código de activação do portal entregue: "
                + patient.fullName()
                + " (processo "
                + patient.recordNumber()
                + ")"));
    return new IssuedCode(code, expires);
  }

  @Transactional
  public PortalStatus setActive(UUID patientId, boolean active) {
    var user = hospitalUser();
    var patient = requirePatient(patientId);
    accounts.setActive(patientId, active);
    if (!active) {
      codes.pendingFor(patientId).forEach(c -> c.revoke(clock.instant()));
    }
    audit.record(
        AuditEntry.success(
            active ? AuditAction.ACTIVATE : AuditAction.PORTAL_DISABLED,
            AuditEntityType.PATIENT,
            patientId,
            user.hospitalId(),
            (active ? "Acesso ao portal reactivado: " : "Acesso ao portal desactivado: ")
                + patient.fullName()));
    return status(patientId);
  }

  private PatientDirectory.PatientRef requirePatient(UUID patientId) {
    return patients
        .find(patientId)
        .orElseThrow(() -> new NotFoundException("PATIENT_NOT_FOUND", "Doente não encontrado."));
  }

  private CurrentUser hospitalUser() {
    var user = currentUser.get();
    if (user.hospitalId() == null) {
      throw new ForbiddenException(
          "HOSPITAL_SCOPE_REQUIRED", "Só o pessoal de um hospital gere o portal.");
    }
    return user;
  }
}
