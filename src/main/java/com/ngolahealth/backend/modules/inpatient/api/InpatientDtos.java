package com.ngolahealth.backend.modules.inpatient.api;

import com.ngolahealth.backend.modules.inpatient.domain.AdmissionStatus;
import com.ngolahealth.backend.modules.inpatient.domain.BedStatus;
import com.ngolahealth.backend.modules.inpatient.domain.BedType;
import com.ngolahealth.backend.modules.inpatient.domain.DischargeCondition;
import com.ngolahealth.backend.modules.inpatient.domain.GenderPolicy;
import com.ngolahealth.backend.modules.inpatient.domain.WardType;
import com.ngolahealth.backend.modules.patients.domain.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InpatientDtos {

  private InpatientDtos() {}

  // ---------- Enfermarias e camas ----------

  public record WardRequest(
      @NotBlank(message = "Indique o código.")
          @Size(max = 20, message = "O código não pode exceder 20 caracteres.")
          @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Use só letras, números, - e _.")
          String code,
      @NotBlank(message = "Indique o nome.")
          @Size(max = 100, message = "O nome não pode exceder 100 caracteres.")
          String name,
      @NotNull(message = "Seleccione o tipo.") WardType type,
      @NotNull(message = "Indique se é mista, masculina ou feminina.") GenderPolicy genderPolicy,
      @Size(max = 20, message = "O piso não pode exceder 20 caracteres.") String floor,
      @Size(max = 500, message = "As notas não podem exceder 500 caracteres.") String notes,
      Long version) {}

  public record WardResponse(
      UUID id,
      String code,
      String name,
      WardType type,
      GenderPolicy genderPolicy,
      String floor,
      String notes,
      boolean active,
      int beds,
      int available,
      int occupied,
      int cleaning,
      int maintenance,
      long version) {}

  public record BedRequest(
      @NotNull(message = "Seleccione a enfermaria.") UUID wardId,
      @NotBlank(message = "Indique o número da cama.")
          @Size(max = 10, message = "O número não pode exceder 10 caracteres.")
          String code,
      @NotNull(message = "Seleccione o tipo de cama.") BedType type) {}

  public record BedStatusRequest(
      @NotNull(message = "Indique o estado.") BedStatus status,
      @Size(max = 200, message = "A nota não pode exceder 200 caracteres.") String note) {}

  /** Ocupante actual (só nas camas ocupadas). */
  public record Occupant(
      UUID admissionId,
      String admissionNumber,
      UUID patientId,
      String patientName,
      String patientRecordNumber,
      Gender gender,
      Integer age,
      String responsibleDoctorName,
      Instant admittedAt,
      LocalDate expectedDischargeDate) {}

  public record BedResponse(
      UUID id,
      UUID wardId,
      String code,
      BedType type,
      BedStatus status,
      String statusNote,
      boolean active,
      Occupant occupant) {}

  /** Mapa de uma enfermaria: camas com ocupante, numa só ida à BD por tabela. */
  public record WardMap(WardResponse ward, List<BedResponse> beds) {}

  // ---------- Internamentos ----------

  public record AdmitRequest(
      @NotNull(message = "Seleccione o paciente.") UUID patientId,
      @NotNull(message = "Seleccione a cama.") UUID bedId,
      UUID responsibleDoctorId,
      UUID originEpisodeId,
      @NotBlank(message = "Indique o motivo do internamento.")
          @Size(max = 500, message = "O motivo não pode exceder 500 caracteres.")
          String reason,
      LocalDate expectedDischargeDate) {}

  public record TransferRequest(
      @NotNull(message = "Seleccione a cama de destino.") UUID toBedId,
      @NotBlank(message = "Indique o motivo da transferência.")
          @Size(max = 300, message = "O motivo não pode exceder 300 caracteres.")
          String reason) {}

  public record DischargeRequest(
      @NotNull(message = "Indique o estado à saída.") DischargeCondition condition,
      @NotBlank(message = "Indique o diagnóstico final.")
          @Size(max = 4000, message = "O diagnóstico não pode exceder 4000 caracteres.")
          String diagnosis,
      @Size(max = 20000, message = "O resumo não pode exceder 20000 caracteres.") String summary) {}

  public record CancelRequest(
      @NotBlank(message = "Indique o motivo da anulação.")
          @Size(max = 500, message = "O motivo não pode exceder 500 caracteres.")
          String reason) {}

  public record AssignDoctorRequest(@NotNull(message = "Seleccione o médico.") UUID doctorId) {}

  public record ExpectedDischargeRequest(LocalDate expectedDischargeDate) {}

  public record TransferView(
      String fromWardName,
      String fromBedCode,
      String toWardName,
      String toBedCode,
      String reason,
      String transferredByName,
      Instant transferredAt) {}

  public record AdmissionResponse(
      UUID id,
      String number,
      UUID patientId,
      String patientName,
      String patientRecordNumber,
      Gender gender,
      Integer age,
      UUID episodeId,
      UUID originEpisodeId,
      UUID wardId,
      String wardName,
      UUID bedId,
      String bedCode,
      UUID responsibleDoctorId,
      String responsibleDoctorName,
      AdmissionStatus status,
      String reason,
      String admittedByName,
      Instant admittedAt,
      LocalDate expectedDischargeDate,
      long lengthOfStayDays,
      String dischargedByName,
      Instant dischargedAt,
      DischargeCondition dischargeCondition,
      String dischargeDiagnosis,
      String dischargeSummary,
      String cancelledByName,
      Instant cancelledAt,
      String cancellationReason,
      List<TransferView> transfers,
      long version) {}
}
