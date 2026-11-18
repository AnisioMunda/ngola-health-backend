package com.ngolahealth.backend.dev;

import com.ngolahealth.backend.common.clinical.VitalsInput;
import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.dev.DemoRunAs.Actor;
import com.ngolahealth.backend.modules.episodes.api.EpisodeDtos.ClinicalNotesRequest;
import com.ngolahealth.backend.modules.episodes.application.EpisodeService;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.EquipmentRequest;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.FaultRequest;
import com.ngolahealth.backend.modules.equipment.api.EquipmentDtos.MaintenanceRequest;
import com.ngolahealth.backend.modules.equipment.application.EquipmentService;
import com.ngolahealth.backend.modules.equipment.application.FaultService;
import com.ngolahealth.backend.modules.equipment.application.MaintenanceService;
import com.ngolahealth.backend.modules.equipment.domain.Criticality;
import com.ngolahealth.backend.modules.equipment.domain.EquipmentCategory;
import com.ngolahealth.backend.modules.equipment.domain.MaintenanceResult;
import com.ngolahealth.backend.modules.equipment.domain.MaintenanceType;
import com.ngolahealth.backend.modules.hospitals.api.HospitalRequest;
import com.ngolahealth.backend.modules.hospitals.application.HospitalService;
import com.ngolahealth.backend.modules.hospitals.domain.HospitalType;
import com.ngolahealth.backend.modules.hr.api.HrDtos.DecisionRequest;
import com.ngolahealth.backend.modules.hr.api.HrDtos.LeaveRequestBody;
import com.ngolahealth.backend.modules.hr.api.HrDtos.ShiftRequest;
import com.ngolahealth.backend.modules.hr.application.LeaveService;
import com.ngolahealth.backend.modules.hr.application.ShiftService;
import com.ngolahealth.backend.modules.hr.domain.LeaveType;
import com.ngolahealth.backend.modules.hr.domain.ShiftType;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.AdmitRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.BedRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.BedStatusRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.DischargeRequest;
import com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.TransferRequest;
import com.ngolahealth.backend.modules.inpatient.application.AdmissionService;
import com.ngolahealth.backend.modules.inpatient.application.WardService;
import com.ngolahealth.backend.modules.inpatient.domain.BedStatus;
import com.ngolahealth.backend.modules.inpatient.domain.BedType;
import com.ngolahealth.backend.modules.inpatient.domain.DischargeCondition;
import com.ngolahealth.backend.modules.inpatient.domain.GenderPolicy;
import com.ngolahealth.backend.modules.inpatient.domain.WardType;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.CreateLabRequest;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.LabTestRequest;
import com.ngolahealth.backend.modules.laboratory.api.LabDtos.ResultRequest;
import com.ngolahealth.backend.modules.laboratory.application.LabRequestService;
import com.ngolahealth.backend.modules.laboratory.application.LabTestService;
import com.ngolahealth.backend.modules.laboratory.domain.LabPriority;
import com.ngolahealth.backend.modules.laboratory.domain.ResultType;
import com.ngolahealth.backend.modules.laboratory.domain.SampleType;
import com.ngolahealth.backend.modules.laboratory.domain.TestCategory;
import com.ngolahealth.backend.modules.patients.api.PatientDtos.PatientRequest;
import com.ngolahealth.backend.modules.patients.application.PatientService;
import com.ngolahealth.backend.modules.patients.domain.Gender;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.MedicationRequest;
import com.ngolahealth.backend.modules.pharmacy.api.PharmacyDtos.ReceiveStockRequest;
import com.ngolahealth.backend.modules.pharmacy.application.MedicationService;
import com.ngolahealth.backend.modules.pharmacy.application.StockService;
import com.ngolahealth.backend.modules.pharmacy.domain.DosageForm;
import com.ngolahealth.backend.modules.prescriptions.api.PrescriptionDtos.CreatePrescriptionRequest;
import com.ngolahealth.backend.modules.prescriptions.api.PrescriptionDtos.DispenseLineRequest;
import com.ngolahealth.backend.modules.prescriptions.api.PrescriptionDtos.ItemRequest;
import com.ngolahealth.backend.modules.prescriptions.application.PrescriptionService;
import com.ngolahealth.backend.modules.prescriptions.domain.Route;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.AppointmentRequest;
import com.ngolahealth.backend.modules.scheduling.api.SchedulingDtos.ScheduleRequest;
import com.ngolahealth.backend.modules.scheduling.application.AppointmentService;
import com.ngolahealth.backend.modules.scheduling.application.ScheduleService;
import com.ngolahealth.backend.modules.scheduling.domain.AppointmentType;
import com.ngolahealth.backend.modules.triage.api.TriageDtos.CreateTriageRequest;
import com.ngolahealth.backend.modules.triage.application.TriageService;
import com.ngolahealth.backend.modules.triage.domain.TriagePriority;
import com.ngolahealth.backend.modules.users.application.PortalAccounts;
import com.ngolahealth.backend.modules.users.domain.User;
import com.ngolahealth.backend.modules.users.domain.UserProfile;
import com.ngolahealth.backend.modules.users.domain.UserRepository;
import com.ngolahealth.backend.security.Role;
import com.ngolahealth.backend.security.password.PasswordPolicyValidator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Dados de demonstração (só perfil dev, e só com {@code DEMO_DATA=true}): um hospital completo com
 * pessoal, doentes, 30 dias de actividade clínica e o dia de hoje em curso.
 *
 * <p>Tudo é criado pelos serviços da aplicação, como num uso real (numeração, stock, camas,
 * auditoria e notificações coerentes). Depois, as datas da actividade passada são recuadas para os
 * dias certos, para o dashboard e os relatórios terem histórico. Idempotente: se o hospital de
 * demonstração já existir, não faz nada. Semente fixa: gera sempre os mesmos dados.
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.dev.demo-data", havingValue = "true")
@Order(100)
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

  static final String HOSPITAL_CODE = "DEMO-001";
  static final int HISTORY_DAYS = 30;

  private final HospitalService hospitals;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final PatientService patients;
  private final TriageService triage;
  private final EpisodeService episodes;
  private final LabTestService labTests;
  private final LabRequestService labRequests;
  private final MedicationService medications;
  private final StockService stock;
  private final PrescriptionService prescriptions;
  private final WardService wards;
  private final AdmissionService admissions;
  private final ScheduleService schedules;
  private final AppointmentService appointments;
  private final ShiftService shifts;
  private final LeaveService leaves;
  private final EquipmentService equipment;
  private final FaultService faults;
  private final MaintenanceService maintenance;
  private final PortalAccounts portalAccounts;
  private final JdbcTemplate jdbc;
  private final AppProperties app;
  private final Clock clock;

  @Value("${app.dev.admin-password:}")
  private String password;

  private final Random random = new Random(20261118L);

  // Estado do gerador
  private UUID hospitalId;
  private final Map<Role, List<Actor>> staff = new HashMap<>();
  private final List<UUID> patientIds = new ArrayList<>();
  private final Map<UUID, Gender> genders = new HashMap<>();
  private final Map<String, UUID> labTestIds = new HashMap<>();
  private final List<UUID> medicationIds = new ArrayList<>();
  private final Map<GenderPolicy, List<UUID>> wardsByPolicy = new HashMap<>();
  private final Map<UUID, List<UUID>> bedsByWard = new HashMap<>();

  @Override
  public void run(ApplicationArguments args) {
    if (password == null || !PasswordPolicyValidator.isStrong(password)) {
      log.warn("Dados de demonstração não criados: DEV_ADMIN_PASSWORD vazio ou fraco.");
      return;
    }
    Integer existing =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM hospitals WHERE code = ?", Integer.class, HOSPITAL_CODE);
    if (existing != null && existing > 0) {
      log.info("Dados de demonstração já existem (hospital {}): nada a fazer.", HOSPITAL_CODE);
      return;
    }
    long started = System.currentTimeMillis();
    log.info("A criar dados de demonstração… (pode demorar um minuto)");
    createHospitalAndStaff();
    createPatients(40);
    createCatalogs();
    createWards();
    createSchedules();
    createHistory();
    createToday();
    createAppointments();
    createHr();
    createEquipment();
    createPortalAccount();
    finishingTouches();
    log.info(
        "Dados de demonstração criados em {} s. Hospital {}; utilizadores *.demo com a senha de DEV_ADMIN_PASSWORD.",
        (System.currentTimeMillis() - started) / 1000,
        HOSPITAL_CODE);
  }

  // ---------- Hospital e pessoal ----------

  private void createHospitalAndStaff() {
    var platform =
        new Actor(
            UUID.randomUUID(), "demo-seeder", "Gerador de demonstração", null, Role.SUPER_ADMIN);
    hospitalId =
        DemoRunAs.as(
                platform,
                () ->
                    hospitals.create(
                        new HospitalRequest(
                            "Hospital Demo Ngola",
                            HOSPITAL_CODE,
                            HospitalType.HOSPITAL,
                            "Luanda",
                            "Talatona",
                            "Via S8, Talatona",
                            "+244 222 000 000",
                            "geral@demo.ngolahealth.local",
                            "5417999999",
                            null)))
            .id();
    staffUser("admin.demo", "Administrador Demo", Role.ADMIN, null);
    staffUser("gestor.demo", "Graça Gestora", Role.MANAGER, null);
    staffUser("medico.demo", "Dr. Paulo Neto", Role.DOCTOR, "Medicina Interna");
    staffUser("medica.demo", "Dra. Helena Kiala", Role.DOCTOR, "Pediatria");
    staffUser("enfermeira.demo", "Enf. Rosa Cassoma", Role.NURSE, null);
    staffUser("enfermeiro.demo", "Enf. Mateus Lumbo", Role.NURSE, null);
    staffUser("recepcao.demo", "Celeste Recepção", Role.RECEPTIONIST, null);
    staffUser("farmacia.demo", "Dr. Bento Farmácia", Role.PHARMACIST, null);
    staffUser("lab.demo", "Téc. Lurdes Laboratório", Role.LAB_TECHNICIAN, null);
  }

  private void staffUser(String username, String name, Role role, String specialty) {
    var user =
        new User(
            username,
            passwordEncoder.encode(password),
            new UserProfile(name, username + "@demo.ngolahealth.local", null, specialty, null),
            hospitalId,
            EnumSet.of(role));
    // Demonstração: senha já "definida" (sem troca obrigatória no primeiro acesso).
    user.changePassword(user.getPasswordHash(), clock.instant());
    var saved = users.saveAndFlush(user);
    staff
        .computeIfAbsent(role, r -> new ArrayList<>())
        .add(new Actor(saved.getId(), username, name, hospitalId, role));
  }

  private Actor one(Role role) {
    var list = staff.get(role);
    return list.get(random.nextInt(list.size()));
  }

  private Actor first(Role role) {
    return staff.get(role).getFirst();
  }

  // ---------- Doentes ----------

  private void createPatients(int count) {
    var reception = first(Role.RECEPTIONIST);
    for (int i = 0; i < count; i++) {
      boolean female = random.nextBoolean();
      var given = pick(female ? DemoNames.FEMALE : DemoNames.MALE);
      var name = given + " " + pick(DemoNames.SURNAMES) + " " + pick(DemoNames.SURNAMES);
      var place = pick(DemoNames.PLACES);
      int age = i < 6 ? 1 + random.nextInt(12) : 18 + random.nextInt(65);
      var birth = today().minusYears(age).minusDays(random.nextInt(365));
      var gender = female ? Gender.FEMALE : Gender.MALE;
      var p =
          DemoRunAs.as(
              reception,
              () ->
                  patients.create(
                      new PatientRequest(
                          name,
                          birth,
                          gender,
                          null,
                          null,
                          "+244 9"
                              + (20 + random.nextInt(80))
                              + " "
                              + (100 + random.nextInt(900))
                              + " "
                              + (100 + random.nextInt(900)),
                          null,
                          "Bairro "
                              + pick(
                                  List.of(
                                      "Operário",
                                      "Rangel",
                                      "Sambizanga",
                                      "Prenda",
                                      "Golfe",
                                      "Kilamba")),
                          place[0],
                          place[1],
                          null,
                          null,
                          null,
                          pick(List.of("A+", "O+", "B+", "O-", "AB+")),
                          random.nextInt(6) == 0 ? "Penicilina" : null,
                          random.nextInt(5) == 0 ? "Hipertensão arterial" : null,
                          null,
                          null)));
      patientIds.add(p.id());
      genders.put(p.id(), gender);
    }
  }

  // ---------- Catálogos ----------

  private void createCatalogs() {
    var lab = first(Role.LAB_TECHNICIAN);
    DemoRunAs.run(
        lab,
        () -> {
          labTest(
              "HB",
              "Hemoglobina",
              TestCategory.HEMATOLOGY,
              SampleType.BLOOD,
              "g/dL",
              "12",
              "16",
              null);
          labTest(
              "LEUC",
              "Leucócitos",
              TestCategory.HEMATOLOGY,
              SampleType.BLOOD,
              "10^9/L",
              "4",
              "11",
              null);
          labTest(
              "GLIC",
              "Glicemia",
              TestCategory.BIOCHEMISTRY,
              SampleType.SERUM,
              "mg/dL",
              "70",
              "110",
              null);
          labTest(
              "CREAT",
              "Creatinina",
              TestCategory.BIOCHEMISTRY,
              SampleType.SERUM,
              "mg/dL",
              "0.6",
              "1.2",
              null);
          labTest(
              "GE",
              "Gota espessa (malária)",
              TestCategory.PARASITOLOGY,
              SampleType.BLOOD,
              null,
              null,
              null,
              "Negativo");
          labTest(
              "URINA",
              "Urina tipo II",
              TestCategory.URINALYSIS,
              SampleType.URINE,
              null,
              null,
              null,
              "Normal");
          labTest(
              "VIH",
              "Teste rápido VIH",
              TestCategory.SEROLOGY,
              SampleType.BLOOD,
              null,
              null,
              null,
              "Não reactivo");
        });

    var pharmacist = first(Role.PHARMACIST);
    DemoRunAs.run(
        pharmacist,
        () -> {
          medication(
              "Artemeter + Lumefantrina",
              "20/120 mg",
              DosageForm.TABLET,
              "comprimido",
              true,
              60,
              600);
          medication("Paracetamol", "500 mg", DosageForm.TABLET, "comprimido", false, 100, 900);
          medication("Amoxicilina", "500 mg", DosageForm.CAPSULE, "cápsula", true, 60, 500);
          medication("Ibuprofeno", "400 mg", DosageForm.TABLET, "comprimido", false, 50, 300);
          medication(
              "Sais de reidratação oral", "20,5 g", DosageForm.POWDER, "saqueta", false, 40, 200);
          medication("Ceftriaxona", "1 g", DosageForm.INJECTION, "ampola", true, 20, 60);
          medication(
              "Metformina",
              "850 mg",
              DosageForm.TABLET,
              "comprimido",
              true,
              60,
              25); // abaixo do mínimo
          medication("Amlodipina", "5 mg", DosageForm.TABLET, "comprimido", true, 60, 400);
          medication("Omeprazol", "20 mg", DosageForm.CAPSULE, "cápsula", true, 40, 300);
          medication("Salbutamol", "100 mcg/dose", DosageForm.INHALER, "inalador", true, 10, 30);
        });
    // Um lote a expirar dentro de 20 dias (alerta da farmácia).
    DemoRunAs.run(
        pharmacist,
        () ->
            stock.receive(
                new ReceiveStockRequest(
                    medicationIds.get(1),
                    "PAR-EXP-01",
                    today().plusDays(20),
                    120,
                    new BigDecimal("15.00"),
                    "Medicamentos de Angola, Lda")));
  }

  private void labTest(
      String code,
      String name,
      TestCategory cat,
      SampleType sample,
      String unit,
      String low,
      String high,
      String text) {
    var t =
        labTests.create(
            new LabTestRequest(
                code,
                name,
                cat,
                sample,
                text == null ? ResultType.NUMERIC : ResultType.QUALITATIVE,
                unit,
                low == null ? null : new BigDecimal(low),
                high == null ? null : new BigDecimal(high),
                text,
                text == null ? 4 : 2,
                null));
    labTestIds.put(code, t.id());
  }

  private void medication(
      String name,
      String strength,
      DosageForm form,
      String unit,
      boolean rx,
      int min,
      int stockQty) {
    var m =
        medications.create(new MedicationRequest(name, null, form, strength, unit, rx, min, null));
    medicationIds.add(m.id());
    stock.receive(
        new ReceiveStockRequest(
            m.id(),
            "L" + (1000 + medicationIds.size()),
            today().plusMonths(6 + random.nextInt(18)),
            stockQty,
            new BigDecimal(20 + random.nextInt(400)),
            "CECOMA"));
  }

  // ---------- Enfermarias ----------

  private void createWards() {
    var admin = first(Role.ADMIN);
    DemoRunAs.run(
        admin,
        () -> {
          ward("MED-H", "Medicina Homens", WardType.MEDICINE, GenderPolicy.MALE, 6);
          ward("MED-M", "Medicina Mulheres", WardType.MEDICINE, GenderPolicy.FEMALE, 6);
          ward("PED", "Pediatria", WardType.PEDIATRICS, GenderPolicy.MIXED, 4);
          ward("UCI", "Cuidados Intensivos", WardType.ICU, GenderPolicy.MIXED, 3);
        });
  }

  private void ward(String code, String name, WardType type, GenderPolicy policy, int beds) {
    var w =
        wards.createWard(
            new com.ngolahealth.backend.modules.inpatient.api.InpatientDtos.WardRequest(
                code, name, type, policy, "1", null, null));
    wardsByPolicy.computeIfAbsent(policy, p -> new ArrayList<>()).add(w.id());
    var ids = new ArrayList<UUID>();
    for (int i = 1; i <= beds; i++) {
      ids.add(
          wards
              .createBed(
                  new BedRequest(
                      w.id(),
                      String.valueOf(i),
                      type == WardType.ICU ? BedType.ICU : BedType.STANDARD))
              .id());
    }
    bedsByWard.put(w.id(), ids);
  }

  // ---------- Agenda ----------

  private void createSchedules() {
    var admin = first(Role.ADMIN);
    DemoRunAs.run(
        admin,
        () -> {
          for (var doctor : staff.get(Role.DOCTOR)) {
            for (var day :
                List.of(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY)) {
              schedules.createSchedule(
                  new ScheduleRequest(
                      doctor.id(), day, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, 1));
            }
          }
        });
  }

  // ---------- Actividade dos últimos 30 dias ----------

  private void createHistory() {
    for (int d = HISTORY_DAYS; d >= 1; d--) {
      int visits = 2 + random.nextInt(3);
      for (int v = 0; v < visits; v++) {
        visit(d, v);
      }
      if (d % 3 == 0) {
        admission(d);
      }
    }
    // A numeração diária da triagem recomeça hoje.
    jdbc.update("DELETE FROM triage_queue_sequences WHERE hospital_id = ?", hospitalId.toString());
  }

  /** Urgência completa: triagem → episódio → (exames) → (receita) → alta. */
  private void visit(int daysAgo, int index) {
    var patientId = patientIds.get(6 + random.nextInt(patientIds.size() - 6));
    var kase = pick(DemoNames.CASES);
    var priority = TriagePriority.valueOf(kase[2]);
    var nurse = one(Role.NURSE);
    var doctor = one(Role.DOCTOR);
    var t =
        DemoRunAs.as(
            nurse,
            () ->
                triage.create(
                    new CreateTriageRequest(
                        patientId,
                        null,
                        null,
                        null,
                        priority,
                        kase[0],
                        2 + random.nextInt(7),
                        null,
                        vitals())));
    DemoRunAs.run(nurse, () -> triage.call(t.id()));
    var opened = DemoRunAs.as(doctor, () -> triage.openEpisode(t.id(), doctor.id()));
    var episodeId = opened.episodeId();
    DemoRunAs.run(nurse, () -> episodes.start(episodeId));

    UUID labRequestId = null;
    if (random.nextInt(2) == 0) {
      labRequestId = labFlow(episodeId, doctor, kase[1]);
    }
    UUID prescriptionId = null;
    if (random.nextInt(3) > 0) {
      prescriptionId = prescriptionFlow(episodeId, doctor);
    }
    DemoRunAs.run(
        doctor,
        () ->
            episodes.recordClinicalNotes(
                episodeId,
                new ClinicalNotesRequest(kase[0], kase[1], "Medicado e orientado.", null)));
    DemoRunAs.run(doctor, () -> episodes.complete(episodeId));

    // Recua as datas para o dia certo (hora do dia variável) e dá uma espera realista à triagem.
    long shiftSeconds = daysAgo * 86_400L - (7 + index * 2 + random.nextInt(2)) * 3_600L;
    int waitMinutes =
        switch (priority) {
          case RED -> random.nextInt(2);
          case ORANGE -> 3 + random.nextInt(15);
          case YELLOW -> 10 + random.nextInt(70);
          case GREEN -> 20 + random.nextInt(120);
          case BLUE -> 40 + random.nextInt(200);
        };
    back(
        "triage_records",
        t.id(),
        shiftSeconds,
        "triaged_at",
        "called_at",
        "closed_at",
        "created_at",
        "updated_at");
    jdbc.update(
        "UPDATE triage_records SET called_at = DATE_ADD(triaged_at, INTERVAL ? MINUTE), queue_date = DATE(DATE_ADD(triaged_at, INTERVAL 1 HOUR)) WHERE id = ?",
        waitMinutes,
        t.id().toString());
    back(
        "episodes",
        episodeId,
        shiftSeconds - 3_600L,
        "scheduled_at",
        "started_at",
        "completed_at",
        "vitals_recorded_at",
        "created_at",
        "updated_at");
    if (labRequestId != null) {
      long requested = shiftSeconds - 4_000L;
      long turnaround = (2 + random.nextInt(7)) * 3_600L + random.nextInt(3_600);
      back("lab_requests", labRequestId, requested, "requested_at", "created_at");
      back("lab_requests", labRequestId, requested - 1_800L, "collected_at");
      back("lab_requests", labRequestId, requested - 3_600L, "analysis_started_at");
      back("lab_requests", labRequestId, requested - turnaround, "completed_at", "updated_at");
      jdbc.update(
          "UPDATE lab_request_items SET resulted_at = DATE_SUB(resulted_at, INTERVAL ? SECOND), validated_at = DATE_SUB(validated_at, INTERVAL ? SECOND) WHERE request_id = ?",
          requested - turnaround + 900L,
          requested - turnaround,
          labRequestId.toString());
    }
    if (prescriptionId != null) {
      back(
          "prescriptions",
          prescriptionId,
          shiftSeconds - 5_000L,
          "issued_at",
          "created_at",
          "updated_at");
      jdbc.update(
          "UPDATE prescriptions SET valid_until = DATE_SUB(valid_until, INTERVAL ? DAY) WHERE id = ?",
          daysAgo,
          prescriptionId.toString());
    }
  }

  private UUID labFlow(UUID episodeId, Actor doctor, String diagnosis) {
    boolean malaria = diagnosis.toLowerCase().contains("malária");
    var tests = malaria ? List.of("GE", "HB") : List.of("HB", "LEUC", "GLIC");
    var req =
        DemoRunAs.as(
            doctor,
            () ->
                labRequests.create(
                    new CreateLabRequest(
                        episodeId,
                        random.nextInt(4) == 0 ? LabPriority.URGENT : LabPriority.ROUTINE,
                        diagnosis,
                        tests.stream().map(labTestIds::get).toList())));
    var lab = first(Role.LAB_TECHNICIAN);
    DemoRunAs.run(lab, () -> labRequests.collect(req.id(), null));
    DemoRunAs.run(lab, () -> labRequests.startAnalysis(req.id()));
    var current = DemoRunAs.as(lab, () -> labRequests.view(req.id()));
    for (var item : current.items()) {
      var result =
          switch (item.testCode()) {
            case "HB" -> new ResultRequest(decimal(malaria ? 8.5 : 11, 15.5), null, null, null);
            case "LEUC" -> new ResultRequest(decimal(3.5, 14), null, null, null);
            case "GLIC" -> new ResultRequest(decimal(70, 160), null, null, null);
            case "GE" ->
                malaria
                    ? new ResultRequest(null, "Positivo (P. falciparum, ++)", true, null)
                    : new ResultRequest(null, "Negativo", false, null);
            default -> new ResultRequest(null, "Normal", false, null);
          };
      DemoRunAs.run(lab, () -> labRequests.recordResult(req.id(), item.id(), result));
      DemoRunAs.run(lab, () -> labRequests.validate(req.id(), item.id()));
    }
    return req.id();
  }

  private UUID prescriptionFlow(UUID episodeId, Actor doctor) {
    var med = medicationIds.get(random.nextInt(5)); // os mais comuns, com stock
    var rx =
        DemoRunAs.as(
            doctor,
            () ->
                prescriptions.create(
                    new CreatePrescriptionRequest(
                        episodeId,
                        7,
                        null,
                        List.of(
                            new ItemRequest(
                                med,
                                6 + random.nextInt(18),
                                "1 unidade",
                                "8/8 h",
                                5,
                                Route.ORAL,
                                "Depois das refeições")))));
    if (random.nextInt(5) > 0) {
      var pharmacist = first(Role.PHARMACIST);
      DemoRunAs.run(
          pharmacist,
          () ->
              prescriptions.dispense(
                  rx.id(),
                  rx.items().stream()
                      .map(i -> new DispenseLineRequest(i.id(), i.quantity()))
                      .toList()));
    }
    return rx.id();
  }

  /** Internamento com início há {@code daysAgo} dias; alta se a estadia já terminou. */
  private void admission(int daysAgo) {
    var doctor = one(Role.DOCTOR);
    var nurse = one(Role.NURSE);
    var patientId = patientWithoutActiveAdmission();
    if (patientId == null) {
      return;
    }
    var bed = freeBed(genders.get(patientId));
    if (bed == null) {
      return;
    }
    var admitted =
        DemoRunAs.as(
            doctor,
            () ->
                admissions.admit(
                    new AdmitRequest(
                        patientId, bed, doctor.id(), null, pick(DemoNames.CASES)[1], null)));
    int stay = 2 + random.nextInt(6);
    if (random.nextInt(4) == 0) {
      var other = freeBed(genders.get(patientId));
      if (other != null) {
        DemoRunAs.run(
            nurse,
            () ->
                admissions.transfer(
                    admitted.id(), new TransferRequest(other, "Ajuste de enfermaria")));
      }
    }
    long admittedShift = daysAgo * 86_400L - 10 * 3_600L;
    if (stay < daysAgo) {
      DemoRunAs.run(
          doctor,
          () ->
              admissions.discharge(
                  admitted.id(),
                  new DischargeRequest(
                      DischargeCondition.IMPROVED,
                      admitted.reason(),
                      "Evolução favorável. Alta com medicação oral.")));
      var current = DemoRunAs.as(doctor, () -> admissions.view(admitted.id()));
      DemoRunAs.run(
          nurse,
          () ->
              wards.setBedStatus(current.bedId(), new BedStatusRequest(BedStatus.AVAILABLE, null)));
      jdbc.update(
          "UPDATE admissions SET discharged_at = DATE_SUB(discharged_at, INTERVAL ? SECOND) WHERE id = ?",
          (daysAgo - stay) * 86_400L,
          admitted.id().toString());
      back("episodes", admitted.episodeId(), (daysAgo - stay) * 86_400L, "completed_at");
    }
    jdbc.update(
        "UPDATE admissions SET admitted_at = DATE_SUB(admitted_at, INTERVAL ? SECOND), created_at = DATE_SUB(created_at, INTERVAL ? SECOND) WHERE id = ?",
        admittedShift,
        admittedShift,
        admitted.id().toString());
    jdbc.update(
        "UPDATE admission_transfers SET transferred_at = DATE_SUB(transferred_at, INTERVAL ? SECOND) WHERE admission_id = ?",
        admittedShift - 86_400L,
        admitted.id().toString());
    back(
        "episodes",
        admitted.episodeId(),
        admittedShift,
        "scheduled_at",
        "started_at",
        "created_at");
  }

  private UUID patientWithoutActiveAdmission() {
    for (int tries = 0; tries < 20; tries++) {
      var p = patientIds.get(6 + random.nextInt(patientIds.size() - 6)); // adultos
      Integer active =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM admissions WHERE patient_id = ? AND status = 'ACTIVE'",
              Integer.class,
              p.toString());
      if (active != null && active == 0) {
        return p;
      }
    }
    return null;
  }

  private UUID freeBed(Gender gender) {
    var policies =
        List.of(
            gender == Gender.FEMALE ? GenderPolicy.FEMALE : GenderPolicy.MALE, GenderPolicy.MIXED);
    for (var policy : policies) {
      for (var ward : wardsByPolicy.getOrDefault(policy, List.of())) {
        for (var bed : bedsByWard.get(ward)) {
          String status =
              jdbc.queryForObject(
                  "SELECT status FROM beds WHERE id = ?", String.class, bed.toString());
          if ("AVAILABLE".equals(status)) {
            return bed;
          }
        }
      }
    }
    return null;
  }

  // ---------- Hoje ----------

  /** Fila de triagem com doentes à espera e dois episódios em curso. */
  private void createToday() {
    var nurse = first(Role.NURSE);
    var doctor = first(Role.DOCTOR);
    for (int i = 0; i < 4; i++) {
      var kase = pick(DemoNames.CASES);
      var patientId = pick(patientIds);
      var t =
          DemoRunAs.as(
              nurse,
              () ->
                  triage.create(
                      new CreateTriageRequest(
                          patientId,
                          null,
                          null,
                          null,
                          TriagePriority.valueOf(kase[2]),
                          kase[0],
                          4,
                          null,
                          vitals())));
      jdbc.update(
          "UPDATE triage_records SET triaged_at = DATE_SUB(triaged_at, INTERVAL ? MINUTE) WHERE id = ?",
          10 + i * 15,
          t.id().toString());
      if (i < 2) {
        DemoRunAs.run(nurse, () -> triage.call(t.id()));
        var opened = DemoRunAs.as(doctor, () -> triage.openEpisode(t.id(), doctor.id()));
        DemoRunAs.run(nurse, () -> episodes.start(opened.episodeId()));
      }
    }
  }

  /** Marcações nas próximas duas semanas e algumas já passadas (com chegadas e faltas). */
  private void createAppointments() {
    var reception = first(Role.RECEPTIONIST);
    var now = java.time.LocalDateTime.ofInstant(clock.instant(), app.timeZone());
    var booked = new ArrayList<UUID>();
    for (int day = 1; day <= 14; day++) {
      var date = today().plusDays(day);
      if (date.getDayOfWeek().getValue() > 5) {
        continue;
      }
      for (var doctor : staff.get(Role.DOCTOR)) {
        for (int slot = 0; slot < 8; slot++) {
          if (random.nextInt(3) == 0) {
            continue;
          }
          var time = LocalTime.of(8, 0).plusMinutes(30L * slot);
          var patientId = pick(patientIds);
          try {
            var a =
                DemoRunAs.as(
                    reception,
                    () ->
                        appointments.create(
                            new AppointmentRequest(
                                patientId,
                                doctor.id(),
                                random.nextInt(4) == 0
                                    ? AppointmentType.FOLLOW_UP
                                    : AppointmentType.CONSULTATION,
                                date,
                                time,
                                "Consulta de rotina",
                                null)));
            booked.add(a.id());
          } catch (RuntimeException e) {
            // O mesmo doente já marcado a essa hora: salta.
          }
        }
      }
    }
    // As marcações dos primeiros dias passam a ter sido há duas semanas, com chegada ou falta.
    for (int i = 0; i < Math.min(30, booked.size()); i++) {
      boolean noShow = random.nextInt(6) == 0;
      jdbc.update(
          "UPDATE appointments SET appointment_date = DATE_SUB(appointment_date, INTERVAL 15 DAY), status = ?, "
              + (noShow ? "no_show_at" : "checked_in_at")
              + " = DATE_SUB(?, INTERVAL 15 DAY) WHERE id = ?",
          noShow ? "NO_SHOW" : "CHECKED_IN",
          java.sql.Timestamp.valueOf(now),
          booked.get(i).toString());
    }
  }

  // ---------- RH, equipamentos e portal ----------

  private void createHr() {
    var manager = first(Role.MANAGER);
    var monday =
        today().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    DemoRunAs.run(
        manager,
        () -> {
          for (int d = 0; d < 7; d++) {
            var date = monday.plusDays(d);
            if (date.isBefore(today())) {
              continue;
            }
            var nurses = staff.get(Role.NURSE);
            shifts.create(
                new ShiftRequest(
                    nurses.get(d % 2).id(),
                    ShiftType.MORNING,
                    date,
                    LocalTime.of(8, 0),
                    LocalTime.of(16, 0),
                    null,
                    "Urgência",
                    null));
            shifts.create(
                new ShiftRequest(
                    nurses.get((d + 1) % 2).id(),
                    ShiftType.NIGHT,
                    date,
                    LocalTime.of(20, 0),
                    LocalTime.of(8, 0),
                    null,
                    "Urgência",
                    null));
            for (var doctor : staff.get(Role.DOCTOR)) {
              if (date.getDayOfWeek().getValue() <= 5) {
                shifts.create(
                    new ShiftRequest(
                        doctor.id(),
                        ShiftType.FULL_DAY,
                        date,
                        LocalTime.of(8, 0),
                        LocalTime.of(17, 0),
                        null,
                        "Consulta externa",
                        null));
              }
            }
          }
        });
    var nurse = first(Role.NURSE);
    DemoRunAs.run(
        nurse,
        () ->
            leaves.request(
                new LeaveRequestBody(
                    LeaveType.VACATION,
                    today().plusDays(20),
                    today().plusDays(29),
                    "Férias anuais")));
    var lab = first(Role.LAB_TECHNICIAN);
    var training =
        DemoRunAs.as(
            lab,
            () ->
                leaves.request(
                    new LeaveRequestBody(
                        LeaveType.TRAINING,
                        today().plusDays(10),
                        today().plusDays(11),
                        "Formação em controlo de qualidade")));
    DemoRunAs.run(
        manager, () -> leaves.decide(training.id(), new DecisionRequest(true, "Aprovado.", true)));
  }

  private void createEquipment() {
    var manager = first(Role.MANAGER);
    var uci = wardsByPolicy.get(GenderPolicy.MIXED).get(1);
    DemoRunAs.run(
        manager,
        () -> {
          var vent =
              equipment.create(
                  equip(
                      "VENT-001",
                      "Ventilador pulmonar",
                      EquipmentCategory.LIFE_SUPPORT,
                      Criticality.HIGH,
                      uci,
                      180,
                      null,
                      365,
                      today().plusDays(40)));
          equipment.create(
              equip(
                  "MON-001",
                  "Monitor multiparamétrico",
                  EquipmentCategory.MONITORING,
                  Criticality.MEDIUM,
                  uci,
                  180,
                  null,
                  365,
                  today().minusDays(3)));
          equipment.create(
              equip(
                  "DESF-001",
                  "Desfibrilhador",
                  EquipmentCategory.LIFE_SUPPORT,
                  Criticality.HIGH,
                  null,
                  90,
                  today().plusDays(5),
                  null,
                  null));
          var eco =
              equipment.create(
                  equip(
                      "ECO-001",
                      "Ecógrafo portátil",
                      EquipmentCategory.IMAGING,
                      Criticality.MEDIUM,
                      null,
                      365,
                      null,
                      null,
                      null));
          equipment.create(
              equip(
                  "CENT-001",
                  "Centrífuga de laboratório",
                  EquipmentCategory.LABORATORY,
                  Criticality.LOW,
                  null,
                  180,
                  null,
                  365,
                  today().plusDays(100)));
          equipment.create(
              equip(
                  "CAD-001",
                  "Cadeira de rodas",
                  EquipmentCategory.MOBILITY,
                  Criticality.LOW,
                  null,
                  null,
                  null,
                  null,
                  null));
          maintenance.record(
              eco.id(),
              new MaintenanceRequest(
                  MaintenanceType.PREVENTIVE,
                  today().minusDays(10),
                  "TecnoMed Angola, Lda",
                  "Revisão anual e limpeza de sondas",
                  null,
                  new BigDecimal("45000"),
                  MaintenanceResult.PASSED,
                  null));
          var nurse = first(Role.NURSE);
          DemoRunAs.run(
              nurse,
              () ->
                  faults.report(
                      vent.id(),
                      new FaultRequest("Alarme de baixa pressão dispara sem motivo", true)));
        });
  }

  private EquipmentRequest equip(
      String code,
      String name,
      EquipmentCategory cat,
      Criticality crit,
      UUID ward,
      Integer mInt,
      LocalDate mNext,
      Integer cInt,
      LocalDate cNext) {
    return new EquipmentRequest(
        code,
        name,
        cat,
        crit,
        "Marca Demo",
        "Modelo X",
        "SN-" + code,
        ward,
        ward == null ? "Urgência" : null,
        today().minusYears(2),
        new BigDecimal("1500000"),
        today().plusYears(1),
        mInt,
        mNext,
        cInt,
        cNext,
        null,
        null);
  }

  /** O primeiro doente adulto fica com conta no portal: doente.demo. */
  private void createPortalAccount() {
    var patientId = patientIds.get(6);
    var name =
        jdbc.queryForObject(
            "SELECT full_name FROM patients WHERE id = ?", String.class, patientId.toString());
    portalAccounts.create(
        hospitalId, patientId, name, "doente.demo", "doente.demo@demo.ngolahealth.local", password);
  }

  // ---------- Auxiliares ----------

  private VitalsInput vitals() {
    return new VitalsInput(
        105 + random.nextInt(50),
        65 + random.nextInt(30),
        65 + random.nextInt(50),
        14 + random.nextInt(10),
        decimal(36.2, 39.8),
        92 + random.nextInt(8),
        null,
        null);
  }

  /** Recua as colunas indicadas da linha {@code id}. */
  private void back(String table, UUID id, long seconds, String... columns) {
    var set = new StringBuilder();
    for (var c : columns) {
      if (!set.isEmpty()) {
        set.append(", ");
      }
      set.append(c)
          .append(" = DATE_SUB(")
          .append(c)
          .append(", INTERVAL ")
          .append(seconds)
          .append(" SECOND)");
    }
    jdbc.update("UPDATE " + table + " SET " + set + " WHERE id = ?", id.toString());
  }

  private BigDecimal decimal(double min, double max) {
    return BigDecimal.valueOf(min + random.nextDouble() * (max - min))
        .setScale(1, java.math.RoundingMode.HALF_UP);
  }

  private <T> T pick(List<T> list) {
    return list.get(random.nextInt(list.size()));
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), app.timeZone());
  }

  @SuppressWarnings("unused")
  private Instant now() {
    return clock.instant();
  }

  // ---------- Retoques finais ----------

  /**
   * Torna o "hoje" realista: datas de registo dos doentes espalhadas, internamentos em curso
   * (incluindo UCI e Pediatria), um lote a expirar e o ponto de quem já está de turno.
   */
  private void finishingTouches() {
    // Registos: adultos há 1 mês a 2 anos; as crianças são registos recentes (novas no período).
    for (int i = 0; i < patientIds.size(); i++) {
      int days = i < 6 ? 2 + random.nextInt(25) : 35 + random.nextInt(700);
      jdbc.update(
          "UPDATE patients SET created_at = DATE_SUB(created_at, INTERVAL ? DAY), updated_at = created_at WHERE id = ?",
          days,
          patientIds.get(i).toString());
    }

    // Internamentos em curso: adultos nas enfermarias, uma criança na Pediatria e um caso na UCI.
    var doctor = first(Role.DOCTOR);
    var pediatrician = staff.get(Role.DOCTOR).get(1);
    for (int i = 0; i < 4; i++) {
      var patientId = patientWithoutActiveAdmission();
      var bed = patientId == null ? null : freeBed(genders.get(patientId));
      if (bed != null) {
        var a =
            DemoRunAs.as(
                doctor,
                () ->
                    admissions.admit(
                        new AdmitRequest(
                            patientId,
                            bed,
                            doctor.id(),
                            null,
                            pick(DemoNames.CASES)[1],
                            today().plusDays(3))));
        backAdmission(a.id(), a.episodeId(), (1 + i) * 86_400L - 5 * 3_600L);
      }
    }
    var pedBed = freeBedIn(wardsByPolicy.get(GenderPolicy.MIXED).get(0));
    if (pedBed != null) {
      var child = patientIds.get(0);
      var ped =
          DemoRunAs.as(
              pediatrician,
              () ->
                  admissions.admit(
                      new AdmitRequest(
                          child,
                          pedBed,
                          pediatrician.id(),
                          null,
                          "Malária grave em criança",
                          today().plusDays(4))));
      backAdmission(ped.id(), ped.episodeId(), 2 * 86_400L);
    }
    var icuPatient = patientWithoutActiveAdmission();
    var icuBed = freeBedIn(wardsByPolicy.get(GenderPolicy.MIXED).get(1));
    if (icuPatient != null && icuBed != null) {
      var icu =
          DemoRunAs.as(
              doctor,
              () ->
                  admissions.admit(
                      new AdmitRequest(
                          icuPatient,
                          icuBed,
                          doctor.id(),
                          null,
                          "Síndrome coronária aguda",
                          null)));
      backAdmission(icu.id(), icu.episodeId(), 20 * 3_600L);
    }

    // Lote a expirar em 20 dias (as receitas do histórico já gastaram os primeiros, pela regra
    // FEFO).
    DemoRunAs.run(
        first(Role.PHARMACIST),
        () ->
            stock.receive(
                new ReceiveStockRequest(
                    medicationIds.get(3),
                    "IBU-EXP-01",
                    today().plusDays(20),
                    80,
                    new BigDecimal("12.00"),
                    "Medicamentos de Angola, Lda")));

    // Ponto: quem tem turno já começado registou a entrada (alguns com pequeno atraso); nos turnos
    // já terminados, também a saída (às vezes com horas extra).
    var now = java.sql.Timestamp.from(clock.instant());
    var started =
        jdbc.queryForList(
            "SELECT id, ends_at <= ? AS finished FROM shifts WHERE hospital_id = ? AND status = 'SCHEDULED' AND starts_at <= ?",
            now,
            hospitalId.toString(),
            now);
    for (var row : started) {
      int late = random.nextInt(4) == 0 ? 6 + random.nextInt(15) : random.nextInt(5);
      boolean finished = ((Number) row.get("finished")).intValue() == 1;
      int overtime = finished && random.nextInt(3) == 0 ? 10 + random.nextInt(50) : 0;
      jdbc.update(
          "INSERT INTO attendance_records (id, hospital_id, user_id, shift_id, clock_in, clock_out, minutes_worked,"
              + " late_minutes, overtime_minutes, version, created_at, updated_at)"
              + " SELECT ?, hospital_id, user_id, id, DATE_ADD(starts_at, INTERVAL ? MINUTE),"
              + " IF(?, DATE_ADD(ends_at, INTERVAL ? MINUTE), NULL),"
              + " IF(?, TIMESTAMPDIFF(MINUTE, starts_at, ends_at) - ? + ?, NULL), ?, ?, 0, NOW(6), NOW(6)"
              + " FROM shifts WHERE id = ?",
          UUID.randomUUID().toString(),
          late,
          finished,
          overtime,
          finished,
          late,
          overtime,
          late,
          overtime,
          row.get("id"));
    }
  }

  private UUID freeBedIn(UUID ward) {
    for (var bed : bedsByWard.get(ward)) {
      String status =
          jdbc.queryForObject("SELECT status FROM beds WHERE id = ?", String.class, bed.toString());
      if ("AVAILABLE".equals(status)) {
        return bed;
      }
    }
    return null;
  }

  private void backAdmission(UUID admissionId, UUID episodeId, long seconds) {
    back("admissions", admissionId, seconds, "admitted_at", "created_at");
    back("episodes", episodeId, seconds, "scheduled_at", "started_at", "created_at");
  }
}
