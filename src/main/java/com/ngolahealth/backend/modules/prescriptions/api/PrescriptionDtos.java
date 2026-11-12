package com.ngolahealth.backend.modules.prescriptions.api;

import com.ngolahealth.backend.modules.prescriptions.domain.PrescriptionStatus;
import com.ngolahealth.backend.modules.prescriptions.domain.Route;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PrescriptionDtos {

  private PrescriptionDtos() {}

  public record ItemRequest(
      @NotNull(message = "Seleccione o medicamento.") UUID medicationId,
      @Min(value = 1, message = "A quantidade tem de ser pelo menos 1.")
          @Max(value = 10_000, message = "Quantidade demasiado elevada.")
          int quantity,
      @NotBlank(message = "Indique a dose (ex.: 1 comprimido).")
          @Size(max = 200, message = "A dose não pode exceder 200 caracteres.")
          String dosage,
      @NotBlank(message = "Indique a frequência (ex.: de 8 em 8 horas).")
          @Size(max = 100, message = "A frequência não pode exceder 100 caracteres.")
          String frequency,
      @Min(value = 1, message = "Duração entre 1 e 365 dias.")
          @Max(value = 365, message = "Duração entre 1 e 365 dias.")
          Integer durationDays,
      @NotNull(message = "Seleccione a via de administração.") Route route,
      @Size(max = 500, message = "As instruções não podem exceder 500 caracteres.")
          String instructions) {}

  /**
   * @param validityDays validade em dias (1–365); null = 30
   */
  public record CreatePrescriptionRequest(
      @NotNull(message = "Indique o episódio.") UUID episodeId,
      @Min(value = 1, message = "Validade entre 1 e 365 dias.")
          @Max(value = 365, message = "Validade entre 1 e 365 dias.")
          Integer validityDays,
      @Size(max = 4000, message = "As notas não podem exceder 4000 caracteres.") String notes,
      @NotEmpty(message = "Adicione pelo menos um medicamento.")
          @Size(max = 30, message = "Máximo de 30 medicamentos por prescrição.")
          List<@Valid ItemRequest> items) {}

  public record DispenseLineRequest(
      @NotNull(message = "Indique a linha.") UUID itemId,
      @Min(value = 1, message = "A quantidade tem de ser pelo menos 1.") int quantity) {}

  public record DispenseRequest(
      @NotEmpty(message = "Indique o que dispensar.") List<@Valid DispenseLineRequest> lines) {}

  public record CancelRequest(
      @NotBlank(message = "Indique o motivo do cancelamento.")
          @Size(max = 500, message = "O motivo não pode exceder 500 caracteres.")
          String reason) {}

  /** {@code stockAvailable}: stock utilizável actual na farmácia (aviso, não bloqueia). */
  public record ItemResponse(
      UUID id,
      int lineNumber,
      UUID medicationId,
      String medicationName,
      String dispensingUnit,
      int quantity,
      int quantityDispensed,
      int remaining,
      String dosage,
      String frequency,
      Integer durationDays,
      Route route,
      String instructions,
      int stockAvailable) {}

  /**
   * @param effectiveStatus estado a mostrar: EXPIRED quando aberta e fora da validade
   */
  public record PrescriptionResponse(
      UUID id,
      String number,
      UUID patientId,
      String patientName,
      String patientRecordNumber,
      UUID episodeId,
      UUID prescriberId,
      String prescriberName,
      PrescriptionStatus status,
      String effectiveStatus,
      Instant issuedAt,
      LocalDate validUntil,
      String notes,
      Instant cancelledAt,
      String cancelledByName,
      String cancellationReason,
      List<ItemResponse> items,
      long version) {}
}
