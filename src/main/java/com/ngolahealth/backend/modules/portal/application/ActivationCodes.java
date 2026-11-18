package com.ngolahealth.backend.modules.portal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Códigos de activação: 10 caracteres de um alfabeto sem ambiguidades (sem 0/O, 1/I/L), em dois
 * grupos ("K7M2Q-9XPR4"), ~50 bits de aleatoriedade. Guardam-se só como SHA-256.
 */
final class ActivationCodes {

  private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();

  private ActivationCodes() {}

  static String generate() {
    var sb = new StringBuilder(11);
    for (int i = 0; i < 10; i++) {
      if (i == 5) {
        sb.append('-');
      }
      sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
    }
    return sb.toString();
  }

  /** Normaliza (maiúsculas, sem espaços nem hífenes) e calcula o hash. */
  static String hash(String code) {
    var normalized = code == null ? "" : code.toUpperCase().replaceAll("[^A-Z0-9]", "");
    try {
      var digest =
          MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
