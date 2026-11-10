package com.ngolahealth.backend.modules.users.api;

import com.ngolahealth.backend.modules.users.domain.UserStatus;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.security.password.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Contratos da API de gestão de utilizadores. */
public final class UserDtos {

  private UserDtos() {}

  static final String PHONE_REGEX = "^\\s*$|^\\s*\\+?[0-9 ()-]{6,20}\\s*$";
  static final String PHONE_MESSAGE =
      "Indique um telefone válido (apenas dígitos, espaços, parênteses e hífenes).";

  /**
   * @param hospitalId obrigatório para o SUPER_ADMIN (excepto ao criar outro SUPER_ADMIN); o ADMIN
   *     cria sempre no seu próprio hospital
   * @param temporaryPassword senha temporária, a trocar no primeiro acesso
   */
  public record CreateUserRequest(
      @NotBlank(message = "O nome é obrigatório.")
          @Size(min = 3, max = 200, message = "O nome deve ter entre 3 e 200 caracteres.")
          String fullName,
      @NotBlank(message = "O nome de utilizador é obrigatório.")
          @Pattern(
              regexp = "^\\s*[A-Za-z0-9._-]{3,50}\\s*$",
              message = "Use 3 a 50 letras sem acentos, números, pontos, hífenes ou sublinhados.")
          String username,
      @NotBlank(message = "O correio electrónico é obrigatório.")
          @Email(message = "Indique um correio electrónico válido.")
          @Size(max = 200, message = "O correio electrónico não pode exceder 200 caracteres.")
          String email,
      @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
      @Size(max = 100, message = "A especialidade não pode exceder 100 caracteres.")
          String specialty,
      @Size(max = 50, message = "O número da carteira profissional não pode exceder 50 caracteres.")
          String professionalCardNumber,
      @NotEmpty(message = "Seleccione pelo menos um perfil.") Set<Role> roles,
      UUID hospitalId,
      @NotBlank(message = "Indique a senha temporária.") @ValidPassword String temporaryPassword) {}

  /** O nome de utilizador não se altera; a senha muda-se por reposição. */
  public record UpdateUserRequest(
      @NotBlank(message = "O nome é obrigatório.")
          @Size(min = 3, max = 200, message = "O nome deve ter entre 3 e 200 caracteres.")
          String fullName,
      @NotBlank(message = "O correio electrónico é obrigatório.")
          @Email(message = "Indique um correio electrónico válido.")
          @Size(max = 200, message = "O correio electrónico não pode exceder 200 caracteres.")
          String email,
      @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
      @Size(max = 100, message = "A especialidade não pode exceder 100 caracteres.")
          String specialty,
      @Size(max = 50, message = "O número da carteira profissional não pode exceder 50 caracteres.")
          String professionalCardNumber,
      @NotEmpty(message = "Seleccione pelo menos um perfil.") Set<Role> roles,
      UUID hospitalId,
      Long version) {}

  public record ResetPasswordRequest(
      @NotBlank(message = "Indique a senha temporária.") @ValidPassword String temporaryPassword) {}

  public record UserResponse(
      UUID id,
      String username,
      String fullName,
      String email,
      String phone,
      String specialty,
      String professionalCardNumber,
      List<Role> roles,
      UUID hospitalId,
      String hospitalName,
      UserStatus status,
      boolean mustChangePassword,
      Instant lockedUntil,
      Instant lastLoginAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {}
}
