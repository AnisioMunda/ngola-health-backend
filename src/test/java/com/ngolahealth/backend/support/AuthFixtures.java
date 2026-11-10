package com.ngolahealth.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.ngolahealth.backend.modules.hospitals.domain.Hospital;
import com.ngolahealth.backend.modules.hospitals.domain.HospitalDetails;
import com.ngolahealth.backend.modules.hospitals.domain.HospitalRepository;
import com.ngolahealth.backend.modules.hospitals.domain.HospitalType;
import com.ngolahealth.backend.modules.users.domain.User;
import com.ngolahealth.backend.modules.users.domain.UserProfile;
import com.ngolahealth.backend.modules.users.domain.UserRepository;
import com.ngolahealth.backend.security.Role;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Cria hospitais e utilizadores reais e obtém tokens pelo endpoint de login verdadeiro. */
@Component
public class AuthFixtures {

  public static final String PASSWORD = "Senha-Teste-2026";
  public static final UUID DEFAULT_HOSPITAL =
      UUID.fromString("00000000-0000-0000-0000-000000000100");

  private final UserRepository users;
  private final HospitalRepository hospitals;
  private final PasswordEncoder encoder;

  /** Hash calculado uma vez: o BCrypt é propositadamente lento. */
  private final String passwordHash;

  public AuthFixtures(UserRepository users, HospitalRepository hospitals, PasswordEncoder encoder) {
    this.users = users;
    this.hospitals = hospitals;
    this.encoder = encoder;
    this.passwordHash = encoder.encode(PASSWORD);
  }

  public UUID hospital(String code) {
    return hospitals
        .saveAndFlush(
            new Hospital(
                new HospitalDetails(
                    "Hospital " + code,
                    code,
                    HospitalType.HOSPITAL,
                    "Luanda",
                    null,
                    null,
                    null,
                    null,
                    null)))
        .getId();
  }

  /** Utilizador activo, com a senha {@link #PASSWORD} já definida pelo próprio. */
  public User user(String username, UUID hospitalId, Role... roles) {
    var user = newUser(username, hospitalId, roles);
    user.changePassword(passwordHash, Instant.now());
    return users.saveAndFlush(user);
  }

  /** Utilizador com senha temporária (troca obrigatória pendente). */
  public User userWithTemporaryPassword(String username, UUID hospitalId, Role... roles) {
    return users.saveAndFlush(newUser(username, hospitalId, roles));
  }

  private User newUser(String username, UUID hospitalId, Role... roles) {
    return new User(
        username,
        passwordHash,
        new UserProfile("Utilizador " + username, username + "@teste.ao", null, null, null),
        hospitalId,
        EnumSet.copyOf(List.of(roles)));
  }

  public MvcResult login(MockMvc mockMvc, String login, String password) throws Exception {
    return mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"login":"%s","password":"%s"}"""
                        .formatted(login, password)))
        .andReturn();
  }

  /** Inicia sessão e devolve um pós-processador que envia o token de acesso. */
  public RequestPostProcessor bearer(MockMvc mockMvc, String username) throws Exception {
    return bearerToken(accessToken(mockMvc, username));
  }

  public String accessToken(MockMvc mockMvc, String username) throws Exception {
    var result = login(mockMvc, username, PASSWORD);
    if (result.getResponse().getStatus() != 200) {
      throw new IllegalStateException("Login falhou: " + result.getResponse().getContentAsString());
    }
    return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
  }

  public static RequestPostProcessor bearerToken(String token) {
    return request -> {
      request.addHeader("Authorization", "Bearer " + token);
      return request;
    };
  }
}
