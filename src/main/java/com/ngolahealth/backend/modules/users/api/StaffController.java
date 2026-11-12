package com.ngolahealth.backend.modules.users.api;

import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.modules.users.application.StaffDirectory;
import com.ngolahealth.backend.modules.users.application.StaffDirectory.StaffRef;
import com.ngolahealth.backend.security.Authorities;
import com.ngolahealth.backend.security.CurrentUserProvider;
import com.ngolahealth.backend.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Listas mínimas de profissionais para os ecrãs clínicos (sem dados de conta). */
@RestController
@RequestMapping("/staff")
@RequiredArgsConstructor
@Tag(name = "Profissionais", description = "Profissionais do hospital para selecção")
public class StaffController {

  private final StaffDirectory staff;
  private final CurrentUserProvider currentUser;

  @GetMapping("/doctors")
  @PreAuthorize(Authorities.PATIENT_READERS)
  @Operation(summary = "Médicos activos do hospital")
  public List<StaffRef> doctors() {
    var user = currentUser.get();
    if (user.hospitalId() == null) {
      throw new ForbiddenException(
          "HOSPITAL_SCOPE_REQUIRED", "Esta lista só existe no âmbito de um hospital.");
    }
    return staff.listActive(user.hospitalId(), Role.DOCTOR);
  }
}
