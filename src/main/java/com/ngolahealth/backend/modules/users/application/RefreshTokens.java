package com.ngolahealth.backend.modules.users.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Formato do refresh token opaco: {@code <id da sessão>.<segredo aleatório de 256 bits>}. */
final class RefreshTokens {

  private static final SecureRandom RANDOM = new SecureRandom();

  record Parsed(UUID sessionId, String secret) {}

  private RefreshTokens() {}

  static String newSecret() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String format(UUID sessionId, String secret) {
    return sessionId + "." + secret;
  }

  static Optional<Parsed> parse(String token) {
    if (token == null) {
      return Optional.empty();
    }
    int dot = token.indexOf('.');
    if (dot <= 0 || dot == token.length() - 1) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new Parsed(UUID.fromString(token.substring(0, dot)), token.substring(dot + 1)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  static String hash(String secret) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponível", e);
    }
  }

  /** Comparação em tempo constante. */
  static boolean matches(String hashA, String hashB) {
    return hashA != null
        && hashB != null
        && MessageDigest.isEqual(
            hashA.getBytes(StandardCharsets.US_ASCII), hashB.getBytes(StandardCharsets.US_ASCII));
  }
}
