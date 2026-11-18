package com.ngolahealth.backend.modules.portal.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.modules.audit.application.AuditActor;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory;
import com.ngolahealth.backend.modules.portal.api.PortalDtos.ActivationRequest;
import com.ngolahealth.backend.modules.portal.domain.ActivationCodeRepository;
import com.ngolahealth.backend.modules.users.application.PortalAccounts;
import com.ngolahealth.backend.security.tenant.TenantContext;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Activação da conta pelo próprio doente (endpoint público). O código indica o hospital e o doente;
 * a data de nascimento confirma-o. Mensagens de erro iguais para "código inexistente" e "data
 * errada", para não revelar códigos válidos.
 */
@Slf4j
@Service
public class PortalActivationService {

  static final String INVALID = "Código inválido, expirado ou data de nascimento errada.";

  private final ActivationCodeRepository codes;
  private final PortalAccounts accounts;
  private final PatientDirectory patients;
  private final AuditTrail audit;
  private final Clock clock;
  private final TransactionTemplate tx;

  public PortalActivationService(
      ActivationCodeRepository codes,
      PortalAccounts accounts,
      PatientDirectory patients,
      AuditTrail audit,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.codes = codes;
    this.accounts = accounts;
    this.patients = patients;
    this.audit = audit;
    this.clock = clock;
    this.tx = new TransactionTemplate(transactionManager);
  }

  public void activate(ActivationRequest r) {
    var code =
        codes
            .findByCodeHash(ActivationCodes.hash(r.code()))
            .filter(c -> c.isUsable(clock.instant()))
            .orElseThrow(PortalActivationService::invalid);
    // O doente pertence ao hospital do código: lê-se e cria-se a conta nesse âmbito.
    TenantContext.callAs(
        code.getHospitalId(),
        () -> {
          var patient =
              patients.find(code.getPatientId()).orElseThrow(PortalActivationService::invalid);
          if (!r.birthDate().equals(patient.birthDate())) {
            codes.registerFailure(code.getId());
            throw invalid();
          }
          return tx.execute(
              status -> {
                var locked =
                    codes.findForUpdate(code.getId()).orElseThrow(PortalActivationService::invalid);
                if (!locked.isUsable(clock.instant())) {
                  throw invalid(); // usado entretanto por outro pedido
                }
                var account =
                    accounts.create(
                        code.getHospitalId(),
                        patient.id(),
                        patient.fullName(),
                        r.username().strip(),
                        r.email().strip().toLowerCase(),
                        r.password());
                locked.use(clock.instant());
                codes.saveAndFlush(locked);
                audit.record(
                    AuditEntry.success(
                            AuditAction.PORTAL_ACTIVATED,
                            AuditEntityType.PATIENT,
                            patient.id(),
                            code.getHospitalId(),
                            "Conta do portal activada pelo doente: "
                                + patient.fullName()
                                + " ("
                                + account.username()
                                + ")")
                        .withActor(
                            new AuditActor(account.id(), account.username(), patient.fullName())));
                return account;
              });
        });
  }

  private static BadRequestException invalid() {
    return new BadRequestException("INVALID_ACTIVATION", INVALID);
  }
}
