package com.ngolahealth.backend.modules.users.application;

import com.ngolahealth.backend.common.exception.BadRequestException;
import com.ngolahealth.backend.common.exception.UnauthorizedException;
import com.ngolahealth.backend.config.properties.AuthProperties;
import com.ngolahealth.backend.modules.hospitals.application.HospitalService;
import com.ngolahealth.backend.modules.hospitals.application.HospitalSummary;
import com.ngolahealth.backend.modules.users.api.AuthDtos.ChangePasswordRequest;
import com.ngolahealth.backend.modules.users.api.AuthDtos.LoginRequest;
import com.ngolahealth.backend.modules.users.api.AuthDtos.MeResponse;
import com.ngolahealth.backend.modules.users.api.AuthDtos.TokenResponse;
import com.ngolahealth.backend.modules.users.domain.SessionRevocationReason;
import com.ngolahealth.backend.modules.users.domain.User;
import com.ngolahealth.backend.modules.users.domain.UserRepository;
import com.ngolahealth.backend.modules.users.domain.UserSession;
import com.ngolahealth.backend.modules.users.domain.UserSessionRepository;
import com.ngolahealth.backend.security.CurrentUser;
import com.ngolahealth.backend.security.jwt.AccessTokenIssuer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login, renovação de sessão, logout e troca de senha. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
  static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
  static final String ACCOUNT_INACTIVE = "ACCOUNT_INACTIVE";
  static final String HOSPITAL_INACTIVE = "HOSPITAL_INACTIVE";
  static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";

  private static final String INVALID_CREDENTIALS_MESSAGE = "Utilizador ou senha incorrectos.";

  private final UserRepository users;
  private final UserSessionRepository sessions;
  private final HospitalService hospitals;
  private final PasswordEncoder passwordEncoder;
  private final AccessTokenIssuer accessTokens;
  private final AuthProperties properties;
  private final Clock clock;

  /** Hash usado quando a conta não existe, para que o tempo de resposta não a denuncie. */
  private volatile String dummyHash;

  /**
   * Sem transacção envolvente: o registo da tentativa falhada tem de persistir mesmo quando o login
   * termina com excepção.
   */
  public TokenResponse login(LoginRequest request, String ipAddress, String userAgent) {
    Instant now = clock.instant();
    String login = request.login().strip().toLowerCase(Locale.ROOT);
    Optional<User> found = users.findByLogin(login);
    if (found.isEmpty()) {
      passwordEncoder.matches(request.password(), dummyHash());
      throw new UnauthorizedException(INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
    }
    User user = found.get();
    if (user.isLocked(now)) {
      throw locked(user.getLockedUntil(), now);
    }
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      registerFailure(user, now);
    }
    // A partir daqui a senha está correcta: pode-se explicar porque é que o acesso é recusado.
    if (!user.isActive()) {
      throw new UnauthorizedException(
          ACCOUNT_INACTIVE, "A sua conta está desactivada. Contacte o administrador.");
    }
    ensureHospitalActive(user);

    users.recordSuccessfulLogin(user.getId(), now);
    String secret = RefreshTokens.newSecret();
    var session =
        sessions.save(
            new UserSession(
                user.getId(),
                RefreshTokens.hash(secret),
                now,
                properties.refreshTokenTtl(),
                ipAddress,
                userAgent));
    log.info("Login: utilizador={} sessão={}", user.getUsername(), session.getId());
    return tokens(user, session, secret);
  }

  @Transactional(noRollbackFor = UnauthorizedException.class)
  public TokenResponse refresh(String refreshToken) {
    Instant now = clock.instant();
    var parsed = RefreshTokens.parse(refreshToken).orElseThrow(AuthService::invalidRefreshToken);
    var session =
        sessions.findById(parsed.sessionId()).orElseThrow(AuthService::invalidRefreshToken);
    if (!session.isActive(now)) {
      throw invalidRefreshToken();
    }
    String hash = RefreshTokens.hash(parsed.secret());
    if (!RefreshTokens.matches(hash, session.getTokenHash())) {
      if (session.isRecentlyRotated(hash, now)) {
        // Outro separador renovou há instantes: não é roubo, mas este token já não serve.
        throw invalidRefreshToken();
      }
      session.revoke(now, SessionRevocationReason.REFRESH_TOKEN_REUSED);
      log.warn("Refresh token reutilizado: sessão {} revogada", session.getId());
      throw invalidRefreshToken();
    }
    User user =
        users
            .findById(session.getUserId())
            .filter(u -> u.isActive() && !u.isLocked(now))
            .orElseThrow(AuthService::invalidRefreshToken);
    ensureHospitalActive(user);

    String secret = RefreshTokens.newSecret();
    session.rotate(RefreshTokens.hash(secret), now, properties.refreshTokenTtl());
    return tokens(user, session, secret);
  }

  /** Termina a sessão do token de acesso actual e/ou a do refresh token indicado. Idempotente. */
  @Transactional
  public void logout(Optional<CurrentUser> current, String refreshToken) {
    Instant now = clock.instant();
    current
        .flatMap(user -> sessions.findById(user.sessionId()))
        .ifPresent(s -> s.revoke(now, SessionRevocationReason.LOGOUT));
    RefreshTokens.parse(refreshToken)
        .flatMap(
            parsed ->
                sessions
                    .findById(parsed.sessionId())
                    .filter(
                        s ->
                            RefreshTokens.matches(
                                RefreshTokens.hash(parsed.secret()), s.getTokenHash())))
        .ifPresent(s -> s.revoke(now, SessionRevocationReason.LOGOUT));
  }

  @Transactional(readOnly = true)
  public MeResponse me(CurrentUser current) {
    var user = users.findById(current.id()).orElseThrow(AuthService::invalidRefreshToken);
    return me(user);
  }

  /** Troca a senha e termina todas as outras sessões do utilizador. */
  @Transactional
  public void changePassword(CurrentUser current, ChangePasswordRequest request) {
    Instant now = clock.instant();
    var user = users.findById(current.id()).orElseThrow(AuthService::invalidRefreshToken);
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new BadRequestException("INVALID_CURRENT_PASSWORD", "A senha actual está incorrecta.");
    }
    if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
      throw new BadRequestException(
          "PASSWORD_REUSED", "A nova senha tem de ser diferente da senha actual.");
    }
    user.changePassword(passwordEncoder.encode(request.newPassword()), now);
    users.saveAndFlush(user);
    sessions.revokeAll(
        user.getId(), current.sessionId(), now, SessionRevocationReason.PASSWORD_CHANGED.name());
    log.info("Senha alterada: utilizador={}", user.getUsername());
  }

  private void registerFailure(User user, Instant now) {
    boolean lockExpired = user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now);
    int attempts = (lockExpired ? 0 : user.getFailedLoginAttempts()) + 1;
    if (attempts >= properties.maxFailedAttempts()) {
      Instant until = now.plus(properties.lockDuration());
      users.recordFailedLogin(user.getId(), 0, until);
      log.warn("Conta bloqueada após {} tentativas: utilizador={}", attempts, user.getUsername());
      throw locked(until, now);
    }
    users.recordFailedLogin(user.getId(), attempts, null);
    throw new UnauthorizedException(INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
  }

  private void ensureHospitalActive(User user) {
    if (user.getHospitalId() != null
        && !hospitals
            .findSummary(user.getHospitalId())
            .map(HospitalSummary::active)
            .orElse(false)) {
      throw new UnauthorizedException(
          HOSPITAL_INACTIVE, "O hospital da sua conta está inactivo. Contacte o administrador.");
    }
  }

  private TokenResponse tokens(User user, UserSession session, String secret) {
    var access =
        accessTokens.issue(user.getId(), user.getUsername(), user.getRoles(), session.getId());
    long expiresIn = Duration.between(clock.instant(), access.expiresAt()).toSeconds();
    return new TokenResponse(
        access.value(),
        RefreshTokens.format(session.getId(), secret),
        "Bearer",
        expiresIn,
        me(user));
  }

  private MeResponse me(User user) {
    String hospitalName =
        user.getHospitalId() == null
            ? null
            : hospitals.findSummary(user.getHospitalId()).map(HospitalSummary::name).orElse(null);
    return new MeResponse(
        user.getId(),
        user.getUsername(),
        user.getFullName(),
        user.getEmail(),
        user.getRoles().stream().sorted().toList(),
        user.getHospitalId(),
        hospitalName,
        user.isMustChangePassword());
  }

  private static UnauthorizedException locked(Instant until, Instant now) {
    long minutes = Math.max(1, Duration.between(now, until).toMinutes() + 1);
    return new UnauthorizedException(
        ACCOUNT_LOCKED,
        "Conta temporariamente bloqueada por excesso de tentativas. Tente novamente dentro de "
            + minutes
            + " minuto(s).");
  }

  private static UnauthorizedException invalidRefreshToken() {
    return new UnauthorizedException(
        INVALID_REFRESH_TOKEN, "A sessão expirou. Inicie sessão novamente.");
  }

  private String dummyHash() {
    if (dummyHash == null) {
      dummyHash = passwordEncoder.encode(RefreshTokens.newSecret());
    }
    return dummyHash;
  }
}
