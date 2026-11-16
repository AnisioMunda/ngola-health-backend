package com.ngolahealth.backend.modules.scheduling.api;

import com.ngolahealth.backend.common.web.PageResponse;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.AppointmentRequest;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.AppointmentResponse;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.BlockRequest;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.BlockResponse;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.CancelRequest;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.DayAvailability;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.DaySummary;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.RescheduleRequest;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.ScheduleRequest;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.ScheduleResponse;
import com.ngolahealth.backend.modules.scheduling.application.AppointmentService;
import com.ngolahealth.backend.modules.scheduling.application.ScheduleService;
import com.ngolahealth.backend.modules.scheduling.domain.AppointmentStatus;
import com.ngolahealth.backend.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/scheduling")
@RequiredArgsConstructor
@Tag(name = "Agendamento", description = "Horários dos médicos, ausências, vagas e marcações")
public class SchedulingController {

  private final ScheduleService schedules;
  private final AppointmentService appointments;

  // ---------- Horários ----------

  @GetMapping("/schedules")
  @PreAuthorize(Authorities.SCHEDULE_READERS)
  @Operation(summary = "Horários semanais (de um médico ou de todos)")
  public List<ScheduleResponse> schedules(
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(defaultValue = "false") boolean includeInactive) {
    return schedules.schedules(doctorId, includeInactive);
  }

  @PostMapping("/schedules")
  @PreAuthorize(Authorities.SCHEDULE_MANAGERS)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Criar horário semanal")
  public ScheduleResponse createSchedule(@Valid @RequestBody ScheduleRequest request) {
    return schedules.createSchedule(request);
  }

  @PatchMapping("/schedules/{id}/deactivate")
  @PreAuthorize(Authorities.SCHEDULE_MANAGERS)
  @Operation(summary = "Desactivar horário (as marcações futuras mantêm-se)")
  public ScheduleResponse deactivateSchedule(@PathVariable UUID id) {
    return schedules.deactivateSchedule(id);
  }

  // ---------- Ausências ----------

  @GetMapping("/blocks")
  @PreAuthorize(Authorities.SCHEDULE_READERS)
  public List<BlockResponse> blocks(
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return schedules.blocks(doctorId, from, to);
  }

  @PostMapping("/blocks")
  @PreAuthorize(Authorities.SCHEDULE_BLOCKERS)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Registar ausência; devolve as marcações afectadas")
  public BlockResponse createBlock(@Valid @RequestBody BlockRequest request) {
    return schedules.createBlock(request);
  }

  @DeleteMapping("/blocks/{id}")
  @PreAuthorize(Authorities.SCHEDULE_BLOCKERS)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteBlock(@PathVariable UUID id) {
    schedules.deleteBlock(id);
  }

  // ---------- Vagas ----------

  @GetMapping("/availability")
  @PreAuthorize(Authorities.SCHEDULE_READERS)
  @Operation(summary = "Vagas de um médico num dia")
  public DayAvailability availability(@RequestParam UUID doctorId, @RequestParam LocalDate date) {
    return schedules.availability(doctorId, date);
  }

  @GetMapping("/calendar")
  @PreAuthorize(Authorities.SCHEDULE_READERS)
  @Operation(summary = "Resumo mensal (vagas livres e marcações por dia)")
  public List<DaySummary> calendar(
      @RequestParam YearMonth month, @RequestParam(required = false) UUID doctorId) {
    return schedules.month(doctorId, month);
  }

  // ---------- Marcações ----------

  @GetMapping("/appointments")
  @PreAuthorize(Authorities.SCHEDULE_READERS)
  @Operation(summary = "Marcações por período (por omissão, hoje)")
  public PageResponse<AppointmentResponse> appointments(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) AppointmentStatus status,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa.")
          int page,
      @RequestParam(defaultValue = "50")
          @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1.")
          @Max(value = 200, message = "O tamanho da página não pode exceder 200.")
          int size) {
    return appointments.search(from, to, doctorId, patientId, status, page, size);
  }

  @GetMapping("/appointments/{id}")
  @PreAuthorize(Authorities.SCHEDULE_READERS)
  public AppointmentResponse appointment(@PathVariable UUID id) {
    return appointments.view(id);
  }

  @PostMapping("/appointments")
  @PreAuthorize(Authorities.APPOINTMENT_BOOKERS)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Marcar consulta numa vaga livre")
  public AppointmentResponse book(@Valid @RequestBody AppointmentRequest request) {
    return appointments.create(request);
  }

  @PatchMapping("/appointments/{id}/confirm")
  @PreAuthorize(Authorities.APPOINTMENT_BOOKERS)
  public AppointmentResponse confirm(@PathVariable UUID id) {
    return appointments.confirm(id);
  }

  @PatchMapping("/appointments/{id}/check-in")
  @PreAuthorize(Authorities.APPOINTMENT_BOOKERS)
  @Operation(summary = "Registar chegada (abre o episódio)")
  public AppointmentResponse checkIn(@PathVariable UUID id) {
    return appointments.checkIn(id);
  }

  @PatchMapping("/appointments/{id}/no-show")
  @PreAuthorize(Authorities.APPOINTMENT_BOOKERS)
  public AppointmentResponse noShow(@PathVariable UUID id) {
    return appointments.noShow(id);
  }

  @PatchMapping("/appointments/{id}/cancel")
  @PreAuthorize(Authorities.APPOINTMENT_BOOKERS)
  public AppointmentResponse cancel(
      @PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
    return appointments.cancel(id, request.reason());
  }

  @PostMapping("/appointments/{id}/reschedule")
  @PreAuthorize(Authorities.APPOINTMENT_BOOKERS)
  @Operation(summary = "Remarcar para outra vaga (a antiga é cancelada na mesma operação)")
  public AppointmentResponse reschedule(
      @PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
    return appointments.reschedule(id, request);
  }
}
