package com.ngolahealth.backend.modules.users.domain;

public enum SessionRevocationReason {
  LOGOUT,
  PASSWORD_CHANGED,
  PASSWORD_RESET,
  USER_DEACTIVATED,
  REFRESH_TOKEN_REUSED
}
