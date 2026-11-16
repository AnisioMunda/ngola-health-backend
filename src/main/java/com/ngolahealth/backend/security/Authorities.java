package com.ngolahealth.backend.security;

/**
 * Expressões reutilizáveis para {@code @PreAuthorize}. Centralizam as regras de acesso e evitam
 * nomes de papéis espalhados pelo código.
 */
public final class Authorities {

  public static final String AUTHENTICATED = "isAuthenticated()";
  public static final String SUPER_ADMIN = "hasRole('SUPER_ADMIN')";

  /** Gerem utilizadores (o ADMIN só no seu hospital). */
  public static final String USER_MANAGERS = "hasAnyRole('SUPER_ADMIN', 'ADMIN')";

  /** Consultam pacientes do próprio hospital (o SUPER_ADMIN não acede a dados clínicos). */
  public static final String PATIENT_READERS =
      "hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE', 'RECEPTIONIST')";

  /** Registam e editam pacientes. */
  public static final String PATIENT_WRITERS =
      "hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST')";

  /** Activam e inactivam pacientes. */
  public static final String PATIENT_ADMINS = "hasRole('ADMIN')";

  /** Agendam episódios e cancelam episódios ainda não iniciados. */
  public static final String EPISODE_SCHEDULERS =
      "hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST')";

  /** Iniciam episódios e registam sinais vitais. */
  public static final String EPISODE_CARE = "hasAnyRole('DOCTOR', 'NURSE')";

  /** Registam anamnese e diagnóstico, concluem e cancelam episódios em curso. */
  public static final String EPISODE_DOCTORS = "hasRole('DOCTOR')";

  /** Fazem triagem: registam, re-triam, chamam, concluem e identificam doentes. */
  public static final String TRIAGE_STAFF = "hasAnyRole('DOCTOR', 'NURSE')";

  /** Dão um doente em espera como ausente (saiu sem ser atendido). */
  public static final String TRIAGE_DESK = "hasAnyRole('DOCTOR', 'NURSE', 'RECEPTIONIST')";

  /** Consultam o catálogo e o stock (os médicos precisam dele para prescrever). */
  public static final String PHARMACY_READERS =
      "hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE', 'PHARMACIST')";

  /** Gerem o catálogo de medicamentos. */
  public static final String CATALOG_MANAGERS = "hasAnyRole('ADMIN', 'PHARMACIST')";

  /** Movimentam stock: recepção, acertos, abates e dispensa. */
  public static final String PHARMACISTS = "hasRole('PHARMACIST')";

  /** Consultam prescrições. */
  public static final String PRESCRIPTION_READERS =
      "hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE', 'PHARMACIST')";

  /** Emitem e cancelam prescrições (acto médico). */
  public static final String PRESCRIBERS = "hasRole('DOCTOR')";

  /** Consultam pedidos e resultados de laboratório. */
  public static final String LAB_READERS =
      "hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE', 'LAB_TECHNICIAN')";

  /** Gerem o catálogo de exames. */
  public static final String LAB_CATALOG = "hasAnyRole('ADMIN', 'LAB_TECHNICIAN')";

  /** Pedem e cancelam exames (acto médico). */
  public static final String LAB_REQUESTERS = "hasRole('DOCTOR')";

  /** Registam a colheita de amostras. */
  public static final String SAMPLE_COLLECTORS = "hasAnyRole('NURSE', 'LAB_TECHNICIAN')";

  /** Analisam, registam, validam e corrigem resultados. */
  public static final String LAB_TECHNICIANS = "hasRole('LAB_TECHNICIAN')";

  /** Consultam a auditoria (o ADMIN e o MANAGER só do próprio hospital). */
  public static final String AUDIT_READERS = "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')";

  /** Consultam utilizadores (o ADMIN e o MANAGER só no seu hospital). */
  public static final String USER_READERS = "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')";

  /** Consultam agendas, vagas e marcações. */
  public static final String SCHEDULE_READERS =
      "hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE', 'RECEPTIONIST')";

  /** Definem os horários semanais dos médicos. */
  public static final String SCHEDULE_MANAGERS = "hasAnyRole('ADMIN', 'MANAGER')";

  /** Registam ausências (o médico só as suas). */
  public static final String SCHEDULE_BLOCKERS = "hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR')";

  /** Marcam, confirmam, remarcam, cancelam e registam chegadas e faltas. */
  public static final String APPOINTMENT_BOOKERS =
      "hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST')";

  /** Consultam enfermarias, mapa de camas e internamentos. */
  public static final String INPATIENT_READERS =
      "hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE', 'RECEPTIONIST')";

  /** Criam e editam enfermarias e camas. */
  public static final String WARD_MANAGERS = "hasAnyRole('ADMIN', 'MANAGER')";

  /** Marcam camas como limpas ou em manutenção. */
  public static final String BED_KEEPERS = "hasAnyRole('ADMIN', 'MANAGER', 'NURSE')";

  /** Internam, dão alta, anulam e mudam o médico responsável (acto médico). */
  public static final String ADMITTERS = "hasRole('DOCTOR')";

  /** Mudam o paciente de cama ou de enfermaria. */
  public static final String BED_TRANSFERRERS = "hasAnyRole('DOCTOR', 'NURSE')";

  /** Consultam documentos de facturação, preços e caixa. */
  public static final String FINANCE_READERS =
      "hasAnyRole('ADMIN', 'MANAGER', 'FINANCIAL', 'RECEPTIONIST')";

  /** Preparam e emitem documentos e recebem pagamentos (caixa). */
  public static final String BILLING_CLERKS = "hasAnyRole('ADMIN', 'FINANCIAL', 'RECEPTIONIST')";

  /** Anulam documentos e emitem notas de crédito. */
  public static final String FINANCE_SUPERVISORS = "hasAnyRole('ADMIN', 'FINANCIAL')";

  /** Gerem a tabela de preços. */
  public static final String PRICE_MANAGERS = "hasAnyRole('ADMIN', 'FINANCIAL')";

  private Authorities() {}
}
