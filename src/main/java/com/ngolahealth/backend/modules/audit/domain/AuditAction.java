package com.ngolahealth.backend.modules.audit.domain;

/** O que aconteceu. Novos módulos acrescentam valores aqui (a coluna é texto, sem CHECK). */
public enum AuditAction {
  CREATE,
  /** Consulta de um registo sensível (ex.: ficha clínica). */
  VIEW,
  UPDATE,
  ACTIVATE,
  DEACTIVATE,
  LOGIN,
  LOGIN_FAILED,
  ACCOUNT_LOCKED,
  LOGOUT,
  PASSWORD_CHANGED,
  PASSWORD_RESET,
  SESSION_REVOKED,
  ACCESS_DENIED,
  /** Escrita sem evento explícito, registada pela rede de segurança. */
  REQUEST
}
