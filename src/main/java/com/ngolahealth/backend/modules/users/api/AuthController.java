package com.ngolahealth.backend.modules.users.api;

import com.ngolahealth.backend.modules.users.api.AuthDtos.ChangePasswordRequest;
import com.ngolahealth.backend.modules.users.api.AuthDtos.LoginRequest;
import com.ngolahealth.backend.modules.users.api.AuthDtos.LogoutRequest;
import com.ngolahealth.backend.modules.users.api.AuthDtos.MeResponse;
import com.ngolahealth.backend.modules.users.api.AuthDtos.RefreshRequest;
import com.ngolahealth.backend.modules.users.api.AuthDtos.TokenResponse;
import com.ngolahealth.backend.modules.users.application.AuthService;
import com.ngolahealth.backend.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login, sessão e senha")
public class AuthController {

  private final AuthService authService;
  private final CurrentUserProvider currentUser;

  @PostMapping("/login")
  @Operation(summary = "Iniciar sessão com utilizador (ou correio electrónico) e senha")
  public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    return authService.login(request, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Renovar a sessão (o refresh token é trocado a cada utilização)")
  public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return authService.refresh(request.refreshToken());
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Terminar sessão (aceita token de acesso e/ou refresh token)")
  public void logout(@RequestBody(required = false) LogoutRequest request) {
    authService.logout(currentUser.find(), request == null ? null : request.refreshToken());
  }

  @GetMapping("/me")
  @Operation(summary = "Perfil do utilizador autenticado")
  public MeResponse me() {
    return authService.me(currentUser.get());
  }

  @PostMapping("/change-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Alterar a própria senha (termina as outras sessões)")
  public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(currentUser.get(), request);
  }
}
