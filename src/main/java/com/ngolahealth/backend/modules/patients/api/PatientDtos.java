package com.ngolahealth.backend.modules.patients.api;

import com.ngolahealth.backend.modules.patients.domain.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PatientDtos {

  private PatientDtos() {}

  static final String PHONE_REGEX = "^\\s*$|^\\s*\\+?[0-9 ()-]{6,20}\\s*$";
  static final String PHONE_MESSAGE =
      "Indique um telefone válido (apenas dígitos, espaços, parênteses e hífenes).";

  /**
   * @param nationalId BI (9 dígitos, 2 letras, 3 dígitos) ou NIF (10 dígitos)
   * @param version versão lida pelo cliente, para detectar edições concorrentes (só na edição)
   */
  public record PatientRequest(
      @NotBlank(message = "O nome completo é obrigatório.")
          @Size(min = 3, max = 200, message = "O nome deve ter entre 3 e 200 caracteres.")
          String fullName,
      @NotNull(message = "A data de nascimento é obrigatória.")
          @Past(message = "A data de nascimento deve ser anterior à data actual.")
          LocalDate birthDate,
      @NotNull(message = "O género é obrigatório.") Gender gender,
      @Pattern(
              regexp = "(?i)^\\s*(?:|\\d{9}[a-z]{2}\\d{3}|\\d{10})\\s*$",
              message = "O BI deve ter 9 dígitos, 2 letras e 3 dígitos (ou NIF com 10 dígitos).")
          String nationalId,
      @Size(max = 30, message = "O número do cartão de saúde não pode exceder 30 caracteres.")
          String healthCardNumber,
      @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
      @Email(message = "Indique um correio electrónico válido.")
          @Size(max = 200, message = "O correio electrónico não pode exceder 200 caracteres.")
          String email,
      @Size(max = 500, message = "A morada não pode exceder 500 caracteres.") String address,
      @Size(max = 100, message = "A província não pode exceder 100 caracteres.") String province,
      @Size(max = 100, message = "O município não pode exceder 100 caracteres.")
          String municipality,
      @Size(max = 200, message = "O nome do contacto não pode exceder 200 caracteres.")
          String emergencyContactName,
      @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String emergencyContactPhone,
      @Size(max = 50, message = "O parentesco não pode exceder 50 caracteres.")
          String emergencyContactRelationship,
      @Pattern(
              regexp = "^\\s*$|^\\s*(A|B|AB|O)[+-]\\s*$",
              message = "Grupo sanguíneo inválido (A+, A-, B+, B-, AB+, AB-, O+ ou O-).")
          String bloodType,
      @Size(max = 4000, message = "As alergias não podem exceder 4000 caracteres.")
          String allergies,
      @Size(max = 4000, message = "As doenças crónicas não podem exceder 4000 caracteres.")
          String chronicConditions,
      @Size(max = 4000, message = "As observações não podem exceder 4000 caracteres.") String notes,
      Long version) {}

  public record DuplicateCheckRequest(
      @NotBlank(message = "O nome completo é obrigatório.")
          @Size(max = 200, message = "O nome não pode exceder 200 caracteres.")
          String fullName,
      @NotNull(message = "A data de nascimento é obrigatória.") LocalDate birthDate,
      String nationalId,
      String phone) {}

  public record PatientSummary(
      UUID id,
      String recordNumber,
      String fullName,
      LocalDate birthDate,
      int age,
      Gender gender,
      String nationalId,
      String phone,
      boolean active) {}

  public record PatientResponse(
      UUID id,
      String recordNumber,
      String fullName,
      LocalDate birthDate,
      int age,
      Gender gender,
      String nationalId,
      String healthCardNumber,
      String phone,
      String email,
      String address,
      String province,
      String municipality,
      String emergencyContactName,
      String emergencyContactPhone,
      String emergencyContactRelationship,
      String bloodType,
      String allergies,
      String chronicConditions,
      String notes,
      boolean active,
      long version,
      Instant createdAt,
      Instant updatedAt) {}
}
