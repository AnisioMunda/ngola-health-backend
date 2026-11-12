package com.ngolahealth.backend.modules.episodes.api;

import com.ngolahealth.backend.modules.episodes.domain.EpisodeStatus;
import com.ngolahealth.backend.modules.episodes.domain.EpisodeType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class EpisodeDtos {

  private EpisodeDtos() {}

  /**
   * @param scheduledAt null = agora (ex.: urgência)
   */
  public record CreateEpisodeRequest(
      @NotNull(message = "Seleccione o paciente.") UUID patientId,
      @NotNull(message = "Seleccione o tipo de episódio.") EpisodeType type,
      UUID doctorId,
      Instant scheduledAt,
      @NotBlank(message = "Indique o motivo.")
          @Size(max = 500, message = "O motivo não pode exceder 500 caracteres.")
          String reason) {}

  public record RescheduleRequest(
      @NotNull(message = "Seleccione o tipo de episódio.") EpisodeType type,
      UUID doctorId,
      @NotNull(message = "Indique a data e hora.") Instant scheduledAt,
      @NotBlank(message = "Indique o motivo.")
          @Size(max = 500, message = "O motivo não pode exceder 500 caracteres.")
          String reason,
      Long version) {}

  public record AssignDoctorRequest(UUID doctorId) {}

  /** Limites de plausibilidade: valores fora deles são quase certamente erros de digitação. */
  public record VitalsRequest(
      @Min(value = 40, message = "TA sistólica entre 40 e 300 mmHg.")
          @Max(value = 300, message = "TA sistólica entre 40 e 300 mmHg.")
          Integer systolicBp,
      @Min(value = 20, message = "TA diastólica entre 20 e 200 mmHg.")
          @Max(value = 200, message = "TA diastólica entre 20 e 200 mmHg.")
          Integer diastolicBp,
      @Min(value = 20, message = "Frequência cardíaca entre 20 e 250 bpm.")
          @Max(value = 250, message = "Frequência cardíaca entre 20 e 250 bpm.")
          Integer heartRate,
      @Min(value = 4, message = "Frequência respiratória entre 4 e 80 cpm.")
          @Max(value = 80, message = "Frequência respiratória entre 4 e 80 cpm.")
          Integer respiratoryRate,
      @DecimalMin(value = "30.0", message = "Temperatura entre 30,0 e 45,0 °C.")
          @DecimalMax(value = "45.0", message = "Temperatura entre 30,0 e 45,0 °C.")
          BigDecimal temperature,
      @Min(value = 50, message = "Saturação de O₂ entre 50 e 100 %.")
          @Max(value = 100, message = "Saturação de O₂ entre 50 e 100 %.")
          Integer oxygenSaturation,
      @DecimalMin(value = "0.3", message = "Peso entre 0,3 e 500 kg.")
          @DecimalMax(value = "500", message = "Peso entre 0,3 e 500 kg.")
          BigDecimal weightKg,
      @DecimalMin(value = "20", message = "Altura entre 20 e 250 cm.")
          @DecimalMax(value = "250", message = "Altura entre 20 e 250 cm.")
          BigDecimal heightCm,
      Long version) {}

  public record ClinicalNotesRequest(
      @Size(max = 10000, message = "A anamnese não pode exceder 10000 caracteres.") String symptoms,
      @Size(max = 4000, message = "O diagnóstico não pode exceder 4000 caracteres.")
          String diagnosis,
      @Size(max = 10000, message = "As notas não podem exceder 10000 caracteres.")
          String clinicalNotes,
      Long version) {}

  public record CancelRequest(
      @NotBlank(message = "Indique o motivo do cancelamento.")
          @Size(max = 500, message = "O motivo não pode exceder 500 caracteres.")
          String reason) {}

  public record VitalsResponse(
      Integer systolicBp,
      Integer diastolicBp,
      Integer heartRate,
      Integer respiratoryRate,
      BigDecimal temperature,
      Integer oxygenSaturation,
      BigDecimal weightKg,
      BigDecimal heightCm,
      Instant recordedAt,
      String recordedByName) {}

  public record EpisodeSummary(
      UUID id,
      UUID patientId,
      String patientName,
      String patientRecordNumber,
      EpisodeType type,
      EpisodeStatus status,
      Instant scheduledAt,
      String reason,
      UUID doctorId,
      String doctorName) {}

  public record EpisodeResponse(
      UUID id,
      UUID patientId,
      String patientName,
      String patientRecordNumber,
      EpisodeType type,
      EpisodeStatus status,
      Instant scheduledAt,
      String reason,
      UUID doctorId,
      String doctorName,
      String symptoms,
      String diagnosis,
      String clinicalNotes,
      VitalsResponse vitals,
      Instant startedAt,
      String startedByName,
      Instant completedAt,
      String completedByName,
      Instant cancelledAt,
      String cancelledByName,
      String cancellationReason,
      long version,
      Instant createdAt,
      Instant updatedAt) {}
}
