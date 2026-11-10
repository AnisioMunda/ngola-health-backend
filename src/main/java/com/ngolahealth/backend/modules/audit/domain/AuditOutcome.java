package com.ngolahealth.backend.modules.audit.domain;

public enum AuditOutcome {
  SUCCESS,
  /** A operação falhou (ex.: senha errada). */
  FAILURE,
  /** A operação foi recusada por regra de segurança (sem permissão, conta bloqueada…). */
  DENIED
}
