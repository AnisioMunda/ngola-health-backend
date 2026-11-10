package com.ngolahealth.backend.config;

import com.ngolahealth.backend.config.properties.AppProperties;
import com.ngolahealth.backend.security.ProblemJsonSecurityHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Segurança HTTP: API stateless (sem sessão nem CSRF, autenticação por token), CORS restrito ao
 * frontend e tudo fechado por omissão. Os endpoints públicos são listados explicitamente.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  /** Endpoints acessíveis sem autenticação. */
  static final String[] PUBLIC_ENDPOINTS = {"/actuator/health", "/actuator/info", "/error"};

  /** Documentação da API; só fica activa no perfil dev (ver application.yml). */
  static final String[] API_DOCS_ENDPOINTS = {
    "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
  };

  private final AppProperties appProperties;
  private final ProblemJsonSecurityHandler problemJsonSecurityHandler;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    .requestMatchers(API_DOCS_ENDPOINTS)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(problemJsonSecurityHandler)
                    .accessDeniedHandler(problemJsonSecurityHandler));
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(appProperties.baseUrl()));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Hospital-Id"));
    config.setExposedHeaders(List.of("Content-Disposition"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
