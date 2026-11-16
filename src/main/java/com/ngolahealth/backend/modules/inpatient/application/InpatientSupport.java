package com.ngolahealth.backend.modules.inpatient.application;

import com.ngolahealth.backend.common.exception.ForbiddenException;
import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InpatientSupport {

  private final CurrentUserProvider currentUser;
  private final AppProperties app;
  private final Clock clock;

  CurrentUser hospitalUser() {
    var user = currentUser.get();
    if (user.hospitalId() == null) {
      throw new ForbiddenException(
          "HOSPITAL_SCOPE_REQUIRED",
          "O internamento só está acessível a utilizadores de um hospital.");
    }
    return user;
  }

  Instant now() {
    return clock.instant();
  }

  LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), app.timeZone());
  }

  Integer age(LocalDate birthDate) {
    return birthDate == null ? null : Period.between(birthDate, today()).getYears();
  }

  /** Dias de internamento (o dia de entrada conta como dia 1 só depois da primeira noite). */
  long daysBetween(Instant from, Instant to) {
    return java.time.temporal.ChronoUnit.DAYS.between(
        LocalDate.ofInstant(from, app.timeZone()), LocalDate.ofInstant(to, app.timeZone()));
  }

  static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
