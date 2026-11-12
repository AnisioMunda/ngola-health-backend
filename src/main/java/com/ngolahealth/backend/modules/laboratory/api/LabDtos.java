package com.ngolahealth.backend.modules.laboratory.api;

import com.ngolahealth.backend.modules.laboratory.domain.ItemStatus;
import com.ngolahealth.backend.modules.laboratory.domain.LabPriority;
import com.ngolahealth.backend.modules.laboratory.domain.LabRequestStatus;
import com.ngolahealth.backend.modules.laboratory.domain.ResultFlag;
import com.ngolahealth.backend.modules.laboratory.domain.ResultType;
import com.ngolahealth.backend.modules.laboratory.domain.SampleType;
import com.ngolahealth.backend.modules.laboratory.domain.TestCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LabDtos {

  private LabDtos() {}

  public record LabTestRequest(
      @NotBlank(message = "O código é obrigatório.")
          @Pattern(
              regexp = "^\\s*[A-Za-z0-9._-]{2,30}\\s*$",
              message = "Código: 2 a 30 letras, números, pontos ou hífenes.")
          String code,
      @NotBlank(message = "O nome é obrigatório.")
          @Size(max = 200, message = "Máximo de 200 caracteres.")
          String name,
      @NotNull(message = "Seleccione a categoria.") TestCategory category,
      @NotNull(message = "Seleccione o tipo de amostra.") SampleType sampleType,
      @NotNull(message = "Seleccione o tipo de resultado.") ResultType resultType,
      @Size(max = 30, message = "A unidade não pode exceder 30 caracteres.") String unit,
      @Digits(integer = 8, fraction = 4, message = "Valor inválido.") BigDecimal referenceLow,
      @Digits(integer = 8, fraction = 4, message = "Valor inválido.") BigDecimal referenceHigh,
      @Size(max = 200, message = "Máximo de 200 caracteres.") String referenceText,
      @NotNull(message = "Indique o prazo de entrega (horas).")
          @Min(value = 1, message = "Prazo entre 1 e 2160 horas.")
          @Max(value = 2160, message = "Prazo entre 1 e 2160 horas.")
          Integer turnaroundHours,
      Long version) {}

  public record LabTestResponse(
      UUID id,
      String code,
      String name,
      TestCategory category,
      SampleType sampleType,
      ResultType resultType,
      String unit,
      BigDecimal referenceLow,
      BigDecimal referenceHigh,
      String referenceText,
      int turnaroundHours,
      boolean active,
      long version) {}

  public record CreateLabRequest(
      @NotNull(message = "Indique o episódio.") UUID episodeId,
      @NotNull(message = "Seleccione a prioridade.") LabPriority priority,
      @Size(max = 4000, message = "A informação clínica não pode exceder 4000 caracteres.")
          String clinicalInfo,
      @NotEmpty(message = "Seleccione pelo menos um exame.")
          @Size(max = 40, message = "Máximo de 40 exames por pedido.")
          List<@NotNull UUID> testIds) {}

  public record CollectRequest(
      @Size(max = 500, message = "Máximo de 500 caracteres.") String notes) {}

  /**
   * Numérico: {@code numericValue}; qualitativo: {@code textValue} + {@code abnormal} (opcional;
   * ausente = normal). Objecto e não primitivo: o Jackson 3 recusa primitivos em falta.
   */
  public record ResultRequest(
      @Digits(integer = 8, fraction = 4, message = "Valor inválido (máx. 4 casas decimais).")
          BigDecimal numericValue,
      @Size(max = 500, message = "Máximo de 500 caracteres.") String textValue,
      Boolean abnormal,
      @Size(max = 1000, message = "Máximo de 1000 caracteres.") String notes) {}

  public record AmendRequest(
      @Digits(integer = 8, fraction = 4, message = "Valor inválido (máx. 4 casas decimais).")
          BigDecimal numericValue,
      @Size(max = 500, message = "Máximo de 500 caracteres.") String textValue,
      Boolean abnormal,
      @Size(max = 1000, message = "Máximo de 1000 caracteres.") String notes,
      @NotBlank(message = "Indique o motivo da correcção.")
          @Size(max = 500, message = "Máximo de 500 caracteres.")
          String reason) {}

  public record CancelRequest(
      @NotBlank(message = "Indique o motivo do cancelamento.")
          @Size(max = 500, message = "Máximo de 500 caracteres.")
          String reason) {}

  public record AmendmentView(
      BigDecimal previousNumeric,
      String previousText,
      ResultFlag previousFlag,
      BigDecimal newNumeric,
      String newText,
      ResultFlag newFlag,
      String reason,
      String amendedByName,
      Instant amendedAt) {}

  /**
   * Resultado de um exame. Para quem não é do laboratório, os campos de resultado só vêm
   * preenchidos depois de validado ({@code resultVisible}).
   */
  public record ItemResponse(
      UUID id,
      UUID labTestId,
      String testCode,
      String testName,
      ResultType resultType,
      String unit,
      BigDecimal referenceLow,
      BigDecimal referenceHigh,
      String referenceText,
      ItemStatus status,
      boolean resultVisible,
      BigDecimal numericValue,
      String textValue,
      ResultFlag flag,
      String resultNotes,
      Instant resultedAt,
      String resultedByName,
      Instant validatedAt,
      String validatedByName,
      boolean amended,
      List<AmendmentView> amendments) {}

  public record LabRequestResponse(
      UUID id,
      String number,
      UUID patientId,
      String patientName,
      String patientRecordNumber,
      UUID episodeId,
      String requestedByName,
      Instant requestedAt,
      LabPriority priority,
      String clinicalInfo,
      LabRequestStatus status,
      Instant collectedAt,
      String collectedByName,
      String collectionNotes,
      Instant analysisStartedAt,
      Instant completedAt,
      Instant cancelledAt,
      String cancelledByName,
      String cancellationReason,
      boolean hasAbnormal,
      List<ItemResponse> items,
      long version) {}
}
