package com.ngolahealth.backend.security.jwt;

import com.ngolahealth.backend.config.properties.AuthProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Codificação e validação dos tokens de acesso (HS256, emissor e audiência verificados). */
@Configuration
public class JwtConfig {

  public static final String AUDIENCE = "ngolahealth-api";
  public static final String SESSION_CLAIM = "sid";

  @Bean
  JwtEncoder jwtEncoder(AuthProperties properties) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
  }

  @Bean
  JwtDecoder jwtDecoder(AuthProperties properties) {
    var decoder =
        NimbusJwtDecoder.withSecretKey(secretKey(properties))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    OAuth2TokenValidator<Jwt> audience =
        new JwtClaimValidator<java.util.List<String>>(
            "aud", aud -> aud != null && aud.contains(AUDIENCE));
    OAuth2TokenValidator<Jwt> session =
        new JwtClaimValidator<String>(SESSION_CLAIM, sid -> sid != null && !sid.isBlank());
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.issuer()), audience, session));
    return decoder;
  }

  private static SecretKey secretKey(AuthProperties properties) {
    return new SecretKeySpec(properties.jwtSecretBytes(), "HmacSHA256");
  }
}
