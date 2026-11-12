package com.ngolahealth.backend.modules.triage.api;

import com.ngolahealth.backend.common.clinical.VitalsInput;
import com.ngolahealth.backend.modules.patients.domain.Gender;
import com.ngolahealth.backend.modules.triage.domain.TriagePriority;
import com.ngolahealth.backend.modules.triage.domain.TriageStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TriageDtos {

  private TriageDtos() {}

  /**
   * @param patientId paciente registado; se null, {@code tempName} é obrigatório
   * @param priority obrigatória: numa urgência a omissão não pode equivaler a "pouco urgente"
   */
  public record CreateTriageRequest(
      UUID patientId,
      @Size(max = 200, message = "O nome provisório não pode exceder 200 caracteres.")
          String tempName,
      @Min(value = 0, message = "Idade aproximada entre 0 e 130.")
          @Max(value = 130, message = "Idade aproximada entre 0 e 130.")
          Integer tempAge,
      Gender tempGender,
      @NotNull(message = "Seleccione a prioridade.") TriagePriority priority,
      @NotBlank(message = "Indique a queixa principal.")
          @Size(max = 500, message = "A queixa não pode exceder 500 caracteres.")
          String chiefComplaint,
      @Min(value = 0, message = "Escala de dor entre 0 e 10.")
          @Max(value = 10, message = "Escala de dor entre 0 e 10.")
          Integer painScale,
      @Size(max = 4000, message = "As notas não podem exceder 4000 caracteres.") String notes,
      @Valid VitalsInput vitals) {}

  public record ChangePriorityRequest(
      @NotNull(message = "Seleccione a nova prioridade.") TriagePriority priority,
      @NotBlank(message = "Indique o motivo da re-triagem.")
          @Size(max = 500, message = "O motivo não pode exceder 500 caracteres.")
          String reason) {}

  public record IdentifyRequest(@NotNull(message = "Seleccione o paciente.") UUID patientId) {}

  public record OpenEpisodeRequest(UUID doctorId) {}

  public record VitalsView(
      Integer systolicBp,
      Integer diastolicBp,
      Integer heartRate,
      Integer respiratoryRate,
      BigDecimal temperature,
      Integer oxygenSaturation,
      BigDecimal weightKg,
      BigDecimal heightCm) {}

  public record PriorityChangeView(
      TriagePriority from,
      TriagePriority to,
      String reason,
      String changedByName,
      Instant changedAt) {}

  /**
   * @param displayName nome do paciente registado ou o nome provisório
   * @param waitingMinutes espera desde a triagem até à chamada (ou até agora)
   * @param overdue em espera há mais do que o tempo-alvo da prioridade
   */
  public record TriageResponse(
      UUID id,
      LocalDate queueDate,
      int queueNumber,
      UUID patientId,
      String patientRecordNumber,
      String displayName,
      boolean identified,
      Integer tempAge,
      Gender tempGender,
      TriagePriority priority,
      String chiefComplaint,
      Integer painScale,
      String notes,
      VitalsView vitals,
      TriageStatus status,
      Instant triagedAt,
      String triagedByName,
      Instant calledAt,
      String calledByName,
      Instant closedAt,
      String closedByName,
      UUID episodeId,
      long waitingMinutes,
      int targetMinutes,
      boolean overdue,
      List<PriorityChangeView> priorityChanges,
      long version) {}

  /** Indicadores do dia (hora local). */
  public record TriageStats(
      LocalDate day,
      int triagedToday,
      int waiting,
      int inProgress,
      int overdue,
      int leftToday,
      Map<TriagePriority, Integer> waitingByPriority,
      Integer averageWaitMinutesToday) {}
}
