package com.ngolahealth.backend.modules.notifications.domain;

/** Tipo de notificação. Novos módulos acrescentam os seus tipos aqui. */
public enum NotificationType {
  /** Todos os exames de um pedido foram validados. */
  LAB_RESULTS_READY,
  /** Resultado alterado validado antes de o pedido estar concluído. */
  LAB_ABNORMAL_RESULT,
  /** Resultado já validado foi corrigido. */
  LAB_RESULT_AMENDED,
  /** Resumo diário de stock baixo, esgotado ou a expirar. */
  STOCK_ALERTS,
  /** Uma marcação do médico foi cancelada por outra pessoa. */
  APPOINTMENT_CANCELLED,
  /** Uma marcação do médico foi remarcada. */
  APPOINTMENT_RESCHEDULED,
  /** Resumo da agenda do dia seguinte. */
  AGENDA_REMINDER,
  /** O médico ficou responsável por um internamento (ao internar ou por mudança). */
  INPATIENT_ASSIGNED,
  /** Novo pedido de ausência por decidir (gestores). */
  LEAVE_REQUESTED,
  /** O pedido de ausência foi aprovado ou recusado (requerente). */
  LEAVE_DECIDED,
  /** Avaria reportada num equipamento (gestores). */
  EQUIPMENT_FAULT,
  /** Resumo diário de manutenções e calibrações vencidas ou a vencer. */
  EQUIPMENT_DUE,
  SYSTEM
}
