package com.ngolahealth.backend.modules.notifications.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.notifications.api.NotificationDtos.NotificationResponse;
import com.ngolahealth.backend.modules.notifications.api.NotificationDtos.UnreadCount;
import com.ngolahealth.backend.modules.notifications.application.NotificationService;
import com.ngolahealth.backend.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@PreAuthorize(Authorities.AUTHENTICATED)
@Tag(name = "Notificações", description = "Notificações do utilizador autenticado")
public class NotificationController {

  private final NotificationService service;

  @GetMapping
  @Operation(summary = "As minhas notificações, mais recentes primeiro")
  public PageResponse<NotificationResponse> list(
      @RequestParam(defaultValue = "false") boolean unreadOnly,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 100, message = "O tamanho da página não pode exceder 100.")
          int size) {
    return service.list(unreadOnly, page, size);
  }

  @GetMapping("/unread-count")
  @Operation(summary = "Número de notificações por ler (sino)")
  public UnreadCount unreadCount() {
    return service.unreadCount();
  }

  @PatchMapping("/{id}/read")
  @Operation(summary = "Marcar como lida")
  public NotificationResponse markRead(@PathVariable UUID id) {
    return service.markRead(id);
  }

  @PatchMapping("/read-all")
  @Operation(summary = "Marcar todas como lidas")
  public UnreadCount markAllRead() {
    return service.markAllRead();
  }
}
