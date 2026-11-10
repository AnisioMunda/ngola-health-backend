package com.ngolahealth.backend.security.jwt;

import com.ngolahealth.backend.config.properties.AuthProperties;
import com.ngolahealth.backend.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Emite tokens de acesso de curta duração. Os papéis vão no token só a título informativo: a
 * autorização usa sempre o estado actual da base de dados.
 */
@Component
@RequiredArgsConstructor
public class AccessTokenIssuer {

  private final JwtEncoder encoder;
  private final AuthProperties properties;
  private final Clock clock;

  public record AccessToken(String value, Instant expiresAt) {}

  public AccessToken issue(UUID userId, String username, Set<Role> roles, UUID sessionId) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(properties.accessTokenTtl());
    var claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .audience(List.of(JwtConfig.AUDIENCE))
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim(JwtConfig.SESSION_CLAIM, sessionId.toString())
            .claim("username", username)
            .claim("roles", roles.stream().map(Role::name).sorted().toList())
            .build();
    var header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
    var token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new AccessToken(token, expiresAt);
  }
}
