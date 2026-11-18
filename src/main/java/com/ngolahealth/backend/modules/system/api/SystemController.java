package com.ngolahealth.backend.modules.system.api;

import com.ngolahealth.backend.modules.system.api.SystemDtos.SystemStatus;
import com.ngolahealth.backend.modules.system.application.SystemStatusService;
import com.ngolahealth.backend.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sistema", description = "Estado da aplicação para a administração da plataforma")
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

  private final SystemStatusService service;

  @Operation(summary = "Estado da aplicação: base de dados, memória, pedidos, logins e tarefas")
  @GetMapping("/status")
  @PreAuthorize(Authorities.SUPER_ADMIN)
  public SystemStatus status() {
    return service.status();
  }
}
