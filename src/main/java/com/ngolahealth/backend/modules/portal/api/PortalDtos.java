package com.ngolahealth.backend.modules.portal.api;

import com.ngolahealth.backend.security.password.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class PortalDtos {

  private PortalDtos() {}

  // ---------- Pessoal: acesso de um doente ao portal ----------

  public record PortalStatus(
      boolean hasAccount,
      boolean accountActive,
      String username,
      String email,
      Instant lastLoginAt,
      Instant pendingCodeExpiresAt) {}

  /** O código só é mostrado uma vez, ao ser gerado. */
  public record IssuedCode(String code, Instant expiresAt) {}

  // ---------- Activação (pública) ----------

  public record ActivationRequest(
      @NotBlank(message = "Indique o código de activação.")
          @Size(max = 20, message = "Código inválido.")
          String code,
      @NotNull(message = "Indique a data de nascimento.") LocalDate birthDate,
      @NotBlank(message = "Escolha um nome de utilizador.")
          @Pattern(
              regexp = "^\\s*[A-Za-z0-9._-]{3,50}\\s*$",
              message = "Use 3 a 50 letras sem acentos, números, pontos, hífenes ou sublinhados.")
          String username,
      @NotBlank(message = "Indique o correio electrónico.")
          @Email(message = "Indique um correio electrónico válido.")
          @Size(max = 200, message = "O correio electrónico não pode exceder 200 caracteres.")
          String email,
      @NotBlank(message = "Escolha uma senha.") @ValidPassword String password) {}

  // ---------- Portal (o próprio doente) ----------

  public record PortalProfile(
      String fullName,
      String recordNumber,
      LocalDate birthDate,
      String hospitalName,
      String phone,
      String email) {}

  public record PortalAppointment(
      UUID id,
      LocalDate date,
      LocalTime startTime,
      LocalTime endTime,
      String doctorName,
      String type,
      String status,
      String reason,
      boolean cancellable) {}

  public record PortalResult(
      String testName,
      String value,
      String unit,
      String reference,
      String flag,
      Instant validatedAt) {}

  public record PortalLabRequest(
      UUID id,
      String number,
      Instant requestedAt,
      String requestedByName,
      String status,
      List<PortalResult> results,
      int pending) {}

  public record PortalPrescriptionItem(String medication, String dosage, String status) {}

  public record PortalPrescription(
      UUID id,
      String number,
      Instant prescribedAt,
      String doctorName,
      String status,
      LocalDate validUntil,
      List<PortalPrescriptionItem> items) {}

  public record PortalVisit(
      UUID id, Instant date, String type, String status, String doctorName, String reason) {}

  public record CancelRequest(
      @NotBlank(message = "Indique o motivo.")
          @Size(max = 300, message = "Máximo de 300 caracteres.")
          String reason) {}
}
