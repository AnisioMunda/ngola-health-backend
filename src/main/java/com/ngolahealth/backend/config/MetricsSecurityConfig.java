package com.ngolahealth.backend.config;

import com.ngolahealth.backend.config.properties.MetricsProperties;
import com.ngolahealth.backend.security.ProblemJsonSecurityHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadeia própria para {@code /actuator/prometheus}: HTTP Basic com as credenciais de {@link
 * MetricsProperties}, avaliada antes da cadeia principal (JWT). Sem credenciais configuradas,
 * nenhum pedido passa.
 */
@Configuration
@RequiredArgsConstructor
public class MetricsSecurityConfig {

  static final String SCRAPER_ROLE = "METRICS_SCRAPER";

  private final MetricsProperties properties;
  private final ProblemJsonSecurityHandler problemJsonSecurityHandler;

  @Bean
  @Order(1)
  SecurityFilterChain prometheusFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/actuator/prometheus")
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authenticationManager(new ProviderManager(List.of(scraperAuthentication())))
        .httpBasic(basic -> basic.authenticationEntryPoint(problemJsonSecurityHandler))
        .authorizeHttpRequests(
            auth -> {
              if (properties.enabled()) {
                auth.anyRequest().hasRole(SCRAPER_ROLE);
              } else {
                auth.anyRequest().denyAll();
              }
            })
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(problemJsonSecurityHandler)
                    .accessDeniedHandler(problemJsonSecurityHandler));
    return http.build();
  }

  /**
   * Comparação em tempo constante de resumos SHA-256 (sem BCrypt: o scrape repete-se a cada 15s).
   */
  private AuthenticationProvider scraperAuthentication() {
    return new AuthenticationProvider() {
      @Override
      public Authentication authenticate(Authentication authentication) {
        if (!properties.enabled()) {
          throw new BadCredentialsException("Métricas desactivadas.");
        }
        String user = authentication.getName();
        Object credentials = authentication.getCredentials();
        String password = credentials == null ? "" : credentials.toString();
        boolean userOk = MessageDigest.isEqual(sha256(user), sha256(properties.username()));
        boolean passwordOk = MessageDigest.isEqual(sha256(password), sha256(properties.password()));
        if (!(userOk && passwordOk)) {
          throw new BadCredentialsException("Credenciais de métricas inválidas.");
        }
        return UsernamePasswordAuthenticationToken.authenticated(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + SCRAPER_ROLE)));
      }

      @Override
      public boolean supports(Class<?> type) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
      }
    };
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256")
          .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
