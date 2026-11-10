package com.ngolahealth.backend.modules.users.api;

import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.security.password.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Contratos da API de autenticação. */
public final class AuthDtos {

  private AuthDtos() {}

  /**
   * @param login nome de utilizador ou correio electrónico
   */
  public record LoginRequest(
      @NotBlank(message = "Indique o utilizador ou o correio electrónico.")
          @Size(max = 200, message = "Valor demasiado longo.")
          String login,
      @NotBlank(message = "Indique a senha.") @Size(max = 200, message = "Valor demasiado longo.")
          String password) {}

  public record RefreshRequest(
      @NotBlank(message = "O refresh token é obrigatório.") String refreshToken) {}

  public record LogoutRequest(String refreshToken) {}

  public record ChangePasswordRequest(
      @NotBlank(message = "Indique a senha actual.") String currentPassword,
      @NotBlank(message = "Indique a nova senha.") @ValidPassword String newPassword) {}

  public record MeResponse(
      UUID id,
      String username,
      String fullName,
      String email,
      List<Role> roles,
      UUID hospitalId,
      String hospitalName,
      boolean mustChangePassword) {}

  /**
   * @param expiresIn segundos até o token de acesso expirar
   */
  public record TokenResponse(
      String accessToken, String refreshToken, String tokenType, long expiresIn, MeResponse user) {}
}
