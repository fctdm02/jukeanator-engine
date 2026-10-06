package com.djt.jukeanator_engine.domain.location.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The indexed, fast-to-check form of a location's API key: its SHA-256, hex-encoded. A location
 * is found by its key with one hash and one index lookup, instead of a BCrypt check against every
 * location -- so a connect attempt with a wrong key costs master almost nothing, however many
 * locations it serves.
 *
 * <p>A fast hash is safe here, unlike for passwords: an API key is 32 bytes from a
 * {@code SecureRandom} (see {@code LocationServiceImpl.generateApiKey()}), so there is nothing
 * to guess or brute-force from the hash; BCrypt's deliberate slowness only protects
 * low-entropy secrets.
 */
public final class ApiKeyLookup {

  private ApiKeyLookup() {}

  /** @return the lookup value for {@code apiKey}, or null for a null key */
  public static String of(String apiKey) {

    if (apiKey == null) {
      return null;
    }
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(apiKey.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  /** Compares in constant time, so the comparison leaks nothing about the stored value. */
  public static boolean matches(String storedLookup, String apiKey) {

    if (storedLookup == null || apiKey == null) {
      return false;
    }
    return MessageDigest.isEqual(storedLookup.getBytes(StandardCharsets.US_ASCII),
        of(apiKey).getBytes(StandardCharsets.US_ASCII));
  }
}
