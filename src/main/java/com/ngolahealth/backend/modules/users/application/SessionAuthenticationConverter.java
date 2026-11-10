package com.ngolahealth.backend.modules.users.application;

import com.ngolahealth.backend.modules.hospitals.application.HospitalService;
import com.ngolahealth.backend.modules.hospitals.application.HospitalSummary;
import com.ngolahealth.backend.modules.users.domain.User;
import com.ngolahealth.backend.modules.users.domain.UserRepository;
import com.ngolahealth.backend.modules.users.domain.UserSessionRepository;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.UserAuthentication;
import com.ngolahealth.backend.security.jwt.JwtConfig;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Converte um token de acesso válido num {@link CurrentUser}, verificando na base de dados que a
 * sessão continua activa e que o utilizador (e o seu hospital) continuam activos. Qualquer falha
 * resulta em 401: logout, suspensão ou desactivação têm efeito imediato.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private final UserSessionRepository sessions;
  private final UserRepository users;
  private final HospitalService hospitals;
  private final Clock clock;

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    var now = clock.instant();
    UUID userId = parse(jwt.getSubject());
    UUID sessionId = parse(jwt.getClaimAsString(JwtConfig.SESSION_CLAIM));

    sessions
        .findById(sessionId)
        .filter(s -> s.getUserId().equals(userId) && s.isActive(now))
        .orElseThrow(() -> invalid("Sessão terminada ou expirada."));
    User user =
        users
            .findById(userId)
            .filter(u -> u.isActive() && !u.isLocked(now))
            .orElseThrow(() -> invalid("Conta indisponível."));
    if (user.getHospitalId() != null
        && !hospitals
            .findSummary(user.getHospitalId())
            .map(HospitalSummary::active)
            .orElse(false)) {
      throw invalid("Hospital inactivo.");
    }
    return new UserAuthentication(
        new CurrentUser(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getHospitalId(),
            user.getRoles(),
            sessionId,
            user.isMustChangePassword()));
  }

  private static UUID parse(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw invalid("Token inválido.");
    }
  }

  private static InvalidBearerTokenException invalid(String message) {
    return new InvalidBearerTokenException(message);
  }
}
