package com.ngolahealth.backend.modules.users.domain;

/** Estado administrativo da conta. O bloqueio por tentativas falhadas é separado (temporário). */
public enum UserStatus {
  ACTIVE,
  INACTIVE
}
