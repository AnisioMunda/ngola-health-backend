package com.ngolahealth.backend.modules.hr.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.hr.api.HrDtos.AttendanceResponse;
import com.ngolahealth.backend.modules.hr.api.HrDtos.CopyWeekRequest;
import com.ngolahealth.backend.modules.hr.api.HrDtos.CopyWeekResponse;
import com.ngolahealth.backend.modules.hr.api.HrDtos.CorrectionRequest;
import com.ngolahealth.backend.modules.hr.api.HrDtos.DailyPresence;
import com.ngolahealth.backend.modules.hr.api.HrDtos.DecisionRequest;
import com.ngolahealth.backend.modules.hr.api.HrDtos.LeaveRequestBody;
import com.ngolahealth.backend.modules.hr.api.HrDtos.LeaveResponse;
import com.ngolahealth.backend.modules.hr.api.HrDtos.Overview;
import com.ngolahealth.backend.modules.hr.api.HrDtos.ReasonRequest;
import com.ngolahealth.backend.modules.hr.api.HrDtos.ShiftRequest;
import com.ngolahealth.backend.modules.hr.api.HrDtos.ShiftResponse;
import com.ngolahealth.backend.modules.hr.api.HrDtos.StaffResponse;
import com.ngolahealth.backend.modules.hr.application.AttendanceService;
import com.ngolahealth.backend.modules.hr.application.HrOverviewService;
import com.ngolahealth.backend.modules.hr.application.LeaveService;
import com.ngolahealth.backend.modules.hr.application.ShiftService;
import com.ngolahealth.backend.modules.hr.domain.LeaveStatus;
import com.ngolahealth.backend.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hr")
@RequiredArgsConstructor
@Tag(name = "Recursos humanos", description = "Escala, ausências e ponto")
public class HrController {

  private final ShiftService shifts;
  private final LeaveService leaves;
  private final AttendanceService attendance;
  private final HrOverviewService overview;

  @GetMapping("/staff")
  @PreAuthorize(Authorities.STAFF)
  public List<StaffResponse> staff() {
    return shifts.staff();
  }

  @GetMapping("/overview")
  @PreAuthorize(Authorities.HR_MANAGERS)
  public Overview overview() {
    return overview.overview();
  }

  // ---------- Escala ----------

  @GetMapping("/shifts")
  @PreAuthorize(Authorities.STAFF)
  @Operation(summary = "Escala do período (todos vêem a escala do hospital)")
  public List<ShiftResponse> shifts(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) UUID userId,
      @RequestParam(defaultValue = "false") boolean includeCancelled) {
    return shifts.search(from, to, userId, includeCancelled);
  }

  @GetMapping("/shifts/mine")
  @PreAuthorize(Authorities.STAFF)
  public List<ShiftResponse> myShifts(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return shifts.mine(from, to);
  }

  @PostMapping("/shifts")
  @PreAuthorize(Authorities.HR_MANAGERS)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Marcar turno (sem sobreposições nem ausências aprovadas)")
  public ShiftResponse createShift(@Valid @RequestBody ShiftRequest request) {
    return shifts.create(request);
  }

  @PatchMapping("/shifts/{id}/cancel")
  @PreAuthorize(Authorities.HR_MANAGERS)
  public ShiftResponse cancelShift(
      @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
    return shifts.cancel(id, request.reason());
  }

  @PostMapping("/shifts/copy-week")
  @PreAuthorize(Authorities.HR_MANAGERS)
  @Operation(summary = "Copiar a escala de uma semana para outra")
  public CopyWeekResponse copyWeek(@Valid @RequestBody CopyWeekRequest request) {
    return shifts.copyWeek(request.fromWeek(), request.toWeek());
  }

  // ---------- Ausências ----------

  @PostMapping("/leaves")
  @PreAuthorize(Authorities.STAFF)
  @ResponseStatus(HttpStatus.CREATED)
  public LeaveResponse requestLeave(@Valid @RequestBody LeaveRequestBody request) {
    return leaves.request(request);
  }

  @GetMapping("/leaves/mine")
  @PreAuthorize(Authorities.STAFF)
  public PageResponse<LeaveResponse> myLeaves(
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 100, message = "O tamanho da página não pode exceder 100.")
          int size) {
    return leaves.mine(page, size);
  }

  @GetMapping("/leaves")
  @PreAuthorize(Authorities.HR_MANAGERS)
  public PageResponse<LeaveResponse> searchLeaves(
      @RequestParam(required = false) LeaveStatus status,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 100, message = "O tamanho da página não pode exceder 100.")
          int size) {
    return leaves.search(status, userId, from, to, page, size);
  }

  @PostMapping("/leaves/{id}/decision")
  @PreAuthorize(Authorities.HR_MANAGERS)
  @Operation(summary = "Aprovar ou recusar (nunca o próprio pedido)")
  public LeaveResponse decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
    return leaves.decide(id, request);
  }

  @PatchMapping("/leaves/{id}/cancel")
  @PreAuthorize(Authorities.STAFF)
  public LeaveResponse cancelLeave(@PathVariable UUID id) {
    return leaves.cancel(id);
  }

  // ---------- Ponto ----------

  @GetMapping("/attendance/current")
  @PreAuthorize(Authorities.STAFF)
  @Operation(summary = "Entrada em aberto do próprio (204 se não houver)")
  public ResponseEntity<AttendanceResponse> current() {
    return attendance.current().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
  }

  @PostMapping("/attendance/clock-in")
  @PreAuthorize(Authorities.STAFF)
  public AttendanceResponse clockIn() {
    return attendance.clockIn();
  }

  @PostMapping("/attendance/clock-out")
  @PreAuthorize(Authorities.STAFF)
  public AttendanceResponse clockOut() {
    return attendance.clockOut();
  }

  @GetMapping("/attendance/mine")
  @PreAuthorize(Authorities.STAFF)
  public List<AttendanceResponse> myAttendance(
      @RequestParam LocalDate from, @RequestParam LocalDate to) {
    return attendance.mine(from, to);
  }

  @GetMapping("/attendance")
  @PreAuthorize(Authorities.HR_MANAGERS)
  public List<AttendanceResponse> searchAttendance(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) UUID userId) {
    return attendance.search(from, to, userId);
  }

  @PatchMapping("/attendance/{id}")
  @PreAuthorize(Authorities.HR_MANAGERS)
  @Operation(summary = "Corrigir ponto (com motivo; nunca o próprio)")
  public AttendanceResponse correct(
      @PathVariable UUID id, @Valid @RequestBody CorrectionRequest request) {
    return attendance.correct(id, request);
  }

  @GetMapping("/attendance/daily")
  @PreAuthorize(Authorities.HR_MANAGERS)
  @Operation(summary = "Assiduidade do dia: cada turno com o estado de presença")
  public List<DailyPresence> daily(@RequestParam(required = false) LocalDate date) {
    return overview.daily(date);
  }
}
