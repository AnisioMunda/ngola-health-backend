package com.ngolahealth.backend.modules.pharmacy.api;

import com.ngolahealth.backend.modules.pharmacy.domain.DosageForm;
import com.ngolahealth.backend.modules.pharmacy.domain.MovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PharmacyDtos {

  private PharmacyDtos() {}

  public record MedicationRequest(
      @NotBlank(message = "O nome é obrigatório.")
          @Size(max = 200, message = "O nome não pode exceder 200 caracteres.")
          String name,
      @Size(max = 200, message = "A DCI não pode exceder 200 caracteres.") String genericName,
      @NotNull(message = "Seleccione a forma farmacêutica.") DosageForm dosageForm,
      @Size(max = 50, message = "A dosagem não pode exceder 50 caracteres.") String strength,
      @NotBlank(message = "Indique a unidade de dispensa (ex.: comprimido).")
          @Size(max = 30, message = "A unidade não pode exceder 30 caracteres.")
          String dispensingUnit,
      boolean requiresPrescription,
      @Min(value = 0, message = "O stock mínimo não pode ser negativo.")
          @Max(value = 1_000_000, message = "Stock mínimo demasiado elevado.")
          int minStockLevel,
      Long version) {}

  public record ReceiveStockRequest(
      @NotNull(message = "Seleccione o medicamento.") UUID medicationId,
      @NotBlank(message = "Indique o número do lote.")
          @Size(max = 50, message = "O lote não pode exceder 50 caracteres.")
          String batchNumber,
      @NotNull(message = "Indique a validade.") LocalDate expiryDate,
      @Min(value = 1, message = "A quantidade tem de ser pelo menos 1.")
          @Max(value = 10_000_000, message = "Quantidade demasiado elevada.")
          int quantity,
      @DecimalMin(value = "0.00", message = "O custo não pode ser negativo.")
          @Digits(integer = 10, fraction = 2, message = "Custo inválido (máx. 2 casas decimais).")
          BigDecimal unitCost,
      @Size(max = 200, message = "O fornecedor não pode exceder 200 caracteres.")
          String supplier) {}

  /**
   * Acerto de inventário (+/-) ou abate de produto danificado. O abate de lotes expirados tem
   * endpoint próprio.
   */
  public record AdjustStockRequest(
      @NotNull(message = "Seleccione o tipo de acerto.") AdjustmentKind kind,
      @Min(value = 1, message = "A quantidade tem de ser pelo menos 1.") int quantity,
      @NotBlank(message = "Indique o motivo.")
          @Size(max = 500, message = "O motivo não pode exceder 500 caracteres.")
          String reason) {}

  public enum AdjustmentKind {
    /** Contagem física acima do registado. */
    COUNT_SURPLUS,
    /** Contagem física abaixo do registado (perda, extravio). */
    COUNT_SHORTAGE,
    /** Produto danificado ou impróprio. */
    DAMAGED;

    public MovementType movementType() {
      return switch (this) {
        case COUNT_SURPLUS -> MovementType.ADJUSTMENT_IN;
        case COUNT_SHORTAGE -> MovementType.ADJUSTMENT_OUT;
        case DAMAGED -> MovementType.WRITE_OFF_DAMAGED;
      };
    }
  }

  /** Dispensa directa: só para medicamentos de venda livre. */
  public record DispenseRequest(
      @NotNull(message = "Seleccione o medicamento.") UUID medicationId,
      @Min(value = 1, message = "A quantidade tem de ser pelo menos 1.")
          @Max(value = 100_000, message = "Quantidade demasiado elevada.")
          int quantity,
      UUID patientId,
      @Size(max = 500, message = "O motivo não pode exceder 500 caracteres.") String reason) {}

  public record MedicationResponse(
      UUID id,
      String name,
      String genericName,
      DosageForm dosageForm,
      String strength,
      String dispensingUnit,
      boolean requiresPrescription,
      int minStockLevel,
      boolean active,
      int stockAvailable,
      boolean lowStock,
      LocalDate nextExpiry,
      long version) {}

  public record BatchResponse(
      UUID id,
      UUID medicationId,
      String medicationName,
      String batchNumber,
      LocalDate expiryDate,
      int quantityReceived,
      int quantityAvailable,
      BigDecimal unitCost,
      String supplier,
      Instant receivedAt,
      String receivedByName,
      boolean expired,
      long daysToExpiry) {}

  public record MovementResponse(
      UUID id,
      UUID batchId,
      String batchNumber,
      MovementType type,
      int quantity,
      int balanceAfter,
      UUID patientId,
      UUID episodeId,
      UUID referenceId,
      String reason,
      String performedByName,
      Instant performedAt) {}

  public record DispenseLine(
      UUID batchId, String batchNumber, LocalDate expiryDate, int quantity) {}

  public record DispenseResponse(
      UUID medicationId,
      String medicationName,
      int quantity,
      List<DispenseLine> lines,
      int remaining) {}

  public record StockAlerts(
      List<MedicationResponse> lowStock,
      List<BatchResponse> expiringSoon,
      List<BatchResponse> expired) {}
}
