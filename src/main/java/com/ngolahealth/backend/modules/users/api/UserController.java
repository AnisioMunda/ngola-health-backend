package com.ngolahealth.backend.modules.users.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.users.api.UserDtos.CreateUserRequest;
import com.ngolahealth.backend.modules.users.api.UserDtos.ResetPasswordRequest;
import com.ngolahealth.backend.modules.users.api.UserDtos.UpdateUserRequest;
import com.ngolahealth.backend.modules.users.api.UserDtos.UserResponse;
import com.ngolahealth.backend.modules.users.application.UserService;
import com.ngolahealth.backend.modules.users.domain.UserStatus;
import com.ngolahealth.backend.security.Authorities;
import com.ngolahealth.backend.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Utilizadores", description = "Gestão de contas, perfis e senhas")
public class UserController {

  private final UserService service;

  @GetMapping
  @PreAuthorize(Authorities.USER_READERS)
  @Operation(summary = "Pesquisar utilizadores (ADMIN/MANAGER: só do próprio hospital)")
  public PageResponse<UserResponse> search(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Role role,
      @RequestParam(required = false) UserStatus status,
      @RequestParam(required = false) UUID hospitalId,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 100, message = "O tamanho da página não pode exceder 100.")
          int size) {
    return service.search(search, role, status, hospitalId, page, size);
  }

  @GetMapping("/{id}")
  @PreAuthorize(Authorities.USER_READERS)
  @Operation(summary = "Obter utilizador")
  public UserResponse findById(@PathVariable UUID id) {
    return service.findById(id);
  }

  @PostMapping
  @PreAuthorize(Authorities.USER_MANAGERS)
  @Operation(summary = "Criar utilizador com senha temporária")
  public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
    var created = service.create(request);
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PutMapping("/{id}")
  @PreAuthorize(Authorities.USER_MANAGERS)
  @Operation(summary = "Editar utilizador")
  public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
    return service.update(id, request);
  }

  @PatchMapping("/{id}/activate")
  @PreAuthorize(Authorities.USER_MANAGERS)
  @Operation(summary = "Activar utilizador")
  public UserResponse activate(@PathVariable UUID id) {
    return service.setActive(id, true);
  }

  @PatchMapping("/{id}/deactivate")
  @PreAuthorize(Authorities.USER_MANAGERS)
  @Operation(summary = "Desactivar utilizador (termina as sessões abertas)")
  public UserResponse deactivate(@PathVariable UUID id) {
    return service.setActive(id, false);
  }

  @PostMapping("/{id}/reset-password")
  @PreAuthorize(Authorities.USER_MANAGERS)
  @Operation(summary = "Repor senha temporária (desbloqueia a conta e termina sessões)")
  public UserResponse resetPassword(
      @PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
    return service.resetPassword(id, request.temporaryPassword());
  }
}
