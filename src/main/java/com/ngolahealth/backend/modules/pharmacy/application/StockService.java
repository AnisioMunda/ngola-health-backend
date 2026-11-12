package com.ngolahealth.backend.modules.pharmacy.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.ConflictException;
import com.ngolahealth.backend.common.exception.NotFoundException;
import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.audit.application.AuditEntry;
import com.ngolahealth.backend.modules.audit.application.AuditTrail;
import com.ngolahealth.backend.modules.audit.domain.AuditAction;
import com.ngolahealth.backend.modules.audit.domain.AuditEntityType;
import com.ngolahealth.backend.modules.patients.application.PatientDirectory;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.AdjustStockRequest;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.BatchResponse;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.DispenseLine;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.DispenseRequest;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.DispenseResponse;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.MovementResponse;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.ReceiveStockRequest;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.StockAlerts;
import com.ngolahealth.backend.modules.pharmacy.domain.Medication;
import com.ngolahealth.backend.modules.pharmacy.domain.MedicationRepository;
import com.ngolahealth.backend.modules.pharmacy.domain.MovementContext;
import com.ngolahealth.backend.modules.pharmacy.domain.MovementType;
import com.ngolahealth.backend.modules.pharmacy.domain.StockBatch;
import com.ngolahealth.backend.modules.pharmacy.domain.StockBatchRepository;
import com.ngolahealth.backend.modules.pharmacy.domain.StockLedger;
import com.ngolahealth.backend.modules.pharmacy.domain.StockMovementRepository;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock da farmácia. Todas as alterações de saldo passam pelo {@link StockLedger} com os lotes
 * bloqueados, por isso duas dispensas simultâneas nunca vendem a mesma unidade.
 */
@Service
@RequiredArgsConstructor
public class StockService {

  private final MedicationService catalog;
  private final MedicationRepository medications;
  private final StockBatchRepository batches;
  private final StockMovementRepository movements;
  private final StockLedger ledger;
  private final PatientDirectory patients;
  private final StaffDirectory staff;
  private final CurrentUserProvider currentUser;
  private final AuditTrail audit;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<BatchResponse> batches(UUID medicationId) {
    catalog.requireHospitalUser();
    var m = catalog.load(medicationId);
    return toBatchResponses(batches.findByMedication(medicationId), Map.of(m.getId(), m));
  }

  @Transactional(readOnly = true)
  public PageResponse<MovementResponse> movements(UUID medicationId, int page, int size) {
    catalog.requireHospitalUser();
    catalog.load(medicationId);
    var result = movements.findByMedication(medicationId, PageRequest.of(page, size));
    var batchNumbers =
        batches.findByMedication(medicationId).stream()
            .collect(Collectors.toMap(StockBatch::getId, StockBatch::getBatchNumber));
    var names = staff.names(result.getContent().stream().map(mv -> mv.getPerformedBy()).toList());
    return PageResponse.of(
        result,
        mv ->
            new MovementResponse(
                mv.getId(),
                mv.getBatchId(),
                batchNumbers.get(mv.getBatchId()),
                mv.getType(),
                mv.getQuantity(),
                mv.getBalanceAfter(),
                mv.getPatientId(),
                mv.getEpisodeId(),
                mv.getReferenceId(),
                mv.getReason(),
                names.get(mv.getPerformedBy()),
                mv.getPerformedAt()));
  }

  @Transactional
  public BatchResponse receive(ReceiveStockRequest r) {
    var user = currentUser.get();
    catalog.requireHospitalUser();
    var m = catalog.load(r.medicationId());
    if (!m.isActive()) {
      throw new ConflictException(
          "MEDICATION_INACTIVE", "Não é possível receber stock de um medicamento desactivado.");
    }
    var today = catalog.today();
    if (!r.expiryDate().isAfter(today)) {
      throw new BadRequestException(
          "BATCH_EXPIRED", "A validade do lote tem de ser posterior a hoje.");
    }
    var batchNumber = r.batchNumber().strip().toUpperCase(java.util.Locale.ROOT);
    if (batches.existsByMedicationIdAndBatchNumber(m.getId(), batchNumber)) {
      throw new ConflictException(
          "BATCH_DUPLICATE", "O lote " + batchNumber + " já foi registado para este medicamento.");
    }
    var now = clock.instant();
    var batch =
        batches.saveAndFlush(
            new StockBatch(
                m.getId(),
                batchNumber,
                r.expiryDate(),
                r.quantity(),
                r.unitCost(),
                optional(r.supplier()),
                user.id(),
                now));
    ledger.postReceipt(
        batch,
        r.supplier() == null ? "Recepção de stock" : "Recepção: " + r.supplier().strip(),
        user.id(),
        now);
    audit.record(
        entry(
            AuditAction.STOCK_RECEIPT,
            m,
            "Recebidas "
                + r.quantity()
                + " "
                + m.getDispensingUnit()
                + " de "
                + MedicationService.label(m)
                + " (lote "
                + batchNumber
                + ", val. "
                + r.expiryDate()
                + ")"));
    return toBatchResponses(List.of(batch), Map.of(m.getId(), m)).getFirst();
  }

  @Transactional
  public BatchResponse adjust(UUID batchId, AdjustStockRequest r) {
    var user = currentUser.get();
    catalog.requireHospitalUser();
    var batch = lockBatch(batchId);
    var m = catalog.load(batch.getMedicationId());
    var type = r.kind().movementType();
    ledger.post(
        batch,
        type,
        r.quantity(),
        MovementContext.reason(r.reason().strip()),
        user.id(),
        clock.instant());
    audit.record(
        entry(
            type == MovementType.WRITE_OFF_DAMAGED
                ? AuditAction.STOCK_WRITE_OFF
                : AuditAction.STOCK_ADJUSTMENT,
            m,
            describeAdjustment(type, r.quantity(), m, batch) + ": " + r.reason().strip()));
    return toBatchResponses(List.of(batch), Map.of(m.getId(), m)).getFirst();
  }

  /** Abate todo o saldo de um lote expirado. */
  @Transactional
  public BatchResponse writeOffExpired(UUID batchId) {
    var user = currentUser.get();
    catalog.requireHospitalUser();
    var batch = lockBatch(batchId);
    if (!batch.isExpired(catalog.today())) {
      throw new ConflictException("BATCH_NOT_EXPIRED", "O lote ainda está dentro da validade.");
    }
    if (batch.getQuantityAvailable() == 0) {
      throw new ConflictException("BATCH_EMPTY", "O lote já não tem saldo.");
    }
    var m = catalog.load(batch.getMedicationId());
    int quantity = batch.getQuantityAvailable();
    ledger.post(
        batch,
        MovementType.WRITE_OFF_EXPIRED,
        quantity,
        MovementContext.reason("Abate por validade expirada (" + batch.getExpiryDate() + ")"),
        user.id(),
        clock.instant());
    audit.record(
        entry(
            AuditAction.STOCK_WRITE_OFF,
            m,
            "Abatidas "
                + quantity
                + " unidade(s) expiradas de "
                + MedicationService.label(m)
                + " (lote "
                + batch.getBatchNumber()
                + ")"));
    return toBatchResponses(List.of(batch), Map.of(m.getId(), m)).getFirst();
  }

  /** Dispensa directa ao balcão: só medicamentos de venda livre. */
  @Transactional
  public DispenseResponse dispenseOverTheCounter(DispenseRequest r) {
    catalog.requireHospitalUser();
    var m = catalog.load(r.medicationId());
    if (m.isRequiresPrescription()) {
      throw new ConflictException(
          "PRESCRIPTION_REQUIRED",
          MedicationService.label(m) + " exige receita: dispense a partir da prescrição.");
    }
    if (r.patientId() != null && patients.find(r.patientId()).isEmpty()) {
      throw new BadRequestException("PATIENT_NOT_FOUND", "O paciente não existe.");
    }
    var response =
        consumeFefo(
            m, r.quantity(), new MovementContext(r.patientId(), null, null, optional(r.reason())));
    audit.record(
        entry(
            AuditAction.DISPENSE,
            m,
            "Dispensa directa: "
                + r.quantity()
                + " "
                + m.getDispensingUnit()
                + " de "
                + MedicationService.label(m)));
    return response;
  }

  /**
   * Dispensa ligada a uma prescrição (o controlo "exige receita" já está satisfeito). Usado pelo
   * módulo de prescrições; corre na transacção de quem chama.
   */
  @Transactional
  public DispenseResponse dispenseForPrescription(
      UUID medicationId, int quantity, MovementContext context) {
    return consumeFefo(catalog.load(medicationId), quantity, context);
  }

  /**
   * Retira {@code quantity} unidades por FEFO, com os lotes bloqueados. Usado pela dispensa directa
   * e pelo módulo de prescrições. Falha sem alterar nada se não houver stock suficiente.
   */
  @Transactional
  public DispenseResponse consumeFefo(Medication m, int quantity, MovementContext context) {
    var user = currentUser.get();
    if (!m.isActive()) {
      throw new ConflictException(
          "MEDICATION_INACTIVE", "Não é possível dispensar um medicamento desactivado.");
    }
    var usable = batches.lockUsableFefo(m.getId(), catalog.today());
    int available = usable.stream().mapToInt(StockBatch::getQuantityAvailable).sum();
    if (available < quantity) {
      throw new ConflictException(
          "INSUFFICIENT_STOCK",
          "Stock insuficiente de "
              + MedicationService.label(m)
              + ": disponível "
              + available
              + ", pedido "
              + quantity
              + ".");
    }
    var now = clock.instant();
    int remaining = quantity;
    List<DispenseLine> lines = new ArrayList<>();
    for (var batch : usable) {
      if (remaining == 0) {
        break;
      }
      int take = Math.min(remaining, batch.getQuantityAvailable());
      ledger.post(batch, MovementType.DISPENSE, take, context, user.id(), now);
      lines.add(
          new DispenseLine(batch.getId(), batch.getBatchNumber(), batch.getExpiryDate(), take));
      remaining -= take;
    }
    return new DispenseResponse(
        m.getId(), MedicationService.label(m), quantity, lines, available - quantity);
  }

  @Transactional(readOnly = true)
  public StockAlerts alerts(int days) {
    catalog.requireHospitalUser();
    if (days < 1 || days > 365) {
      throw new BadRequestException("INVALID_DAYS", "O prazo deve estar entre 1 e 365 dias.");
    }
    var today = catalog.today();
    var active = medications.findAllActive();
    var stock = catalog.stockInfo(active.stream().map(Medication::getId).toList());
    var low =
        active.stream().map(m -> catalog.toResponse(m, stock)).filter(r -> r.lowStock()).toList();
    var expiring = batches.withBalanceExpiringBy(today.plusDays(days));
    var byId =
        medications
            .findAllScoped(expiring.stream().map(StockBatch::getMedicationId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Medication::getId, Function.identity()));
    var all = toBatchResponses(expiring, byId);
    return new StockAlerts(
        low,
        all.stream().filter(b -> !b.expired()).toList(),
        all.stream().filter(BatchResponse::expired).toList());
  }

  private StockBatch lockBatch(UUID id) {
    return batches
        .findForUpdate(id)
        .orElseThrow(() -> new NotFoundException("BATCH_NOT_FOUND", "Lote não encontrado."));
  }

  private List<BatchResponse> toBatchResponses(List<StockBatch> list, Map<UUID, Medication> meds) {
    var today = catalog.today();
    var names =
        staff.names(list.stream().map(StockBatch::getReceivedBy).filter(Objects::nonNull).toList());
    return list.stream()
        .map(
            b ->
                new BatchResponse(
                    b.getId(),
                    b.getMedicationId(),
                    meds.containsKey(b.getMedicationId())
                        ? MedicationService.label(meds.get(b.getMedicationId()))
                        : null,
                    b.getBatchNumber(),
                    b.getExpiryDate(),
                    b.getQuantityReceived(),
                    b.getQuantityAvailable(),
                    b.getUnitCost(),
                    b.getSupplier(),
                    b.getReceivedAt(),
                    names.get(b.getReceivedBy()),
                    b.isExpired(today),
                    ChronoUnit.DAYS.between(today, b.getExpiryDate())))
        .toList();
  }

  private static String describeAdjustment(MovementType type, int qty, Medication m, StockBatch b) {
    var what =
        qty + " unidade(s) de " + MedicationService.label(m) + " (lote " + b.getBatchNumber() + ")";
    return switch (type) {
      case ADJUSTMENT_IN -> "Acerto de inventário +" + what;
      case ADJUSTMENT_OUT -> "Acerto de inventário −" + what;
      default -> "Abate por dano de " + what;
    };
  }

  private AuditEntry entry(AuditAction action, Medication m, String summary) {
    return AuditEntry.success(
        action, AuditEntityType.MEDICATION, m.getId(), m.getHospitalId(), summary);
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
