package com.djt.jukeanator_engine.domain.license.legacy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Byte-exact port of the legacy licensing crypto (MP3Jukeboxx {@code encrypt()},
 * {@code CryptoUtils} and {@code EncryptDecrypt}). This is copy protection, not security: the
 * algorithms and key material are frozen so existing license files and legacy clients keep working.
 *
 * <ul>
 * <li>License key: MD5 of {@code delimiter + machineId + delimiter}.</li>
 * <li>Symmetric cipher: {@code Cipher.getInstance("AES")} (AES/ECB/PKCS5Padding) with a key
 * derived by PBKDF2WithHmacSHA1 from a fixed password and salt; ciphertext is lowercase hex.</li>
 * <li>Passcodes: SHA-1 over ISO-8859-1 bytes, lowercase hex.</li>
 * </ul>
 *
 * <p>The legacy code used the platform default charset; every input here (machine ids, quarters,
 * hour counts, URL-encoded strings, email addresses) is ASCII, for which ISO-8859-1, windows-1252
 * and UTF-8 all agree.
 */
public final class LegacyCrypto {

  private static final char[] PASSWORD =
      {'5', 'J', 'f', 'W', 'S', 'e', 'r', 'i', 't', 'Y', '3', '1'};
  private static final byte[] SALT = "300".getBytes(StandardCharsets.US_ASCII);
  private static final int ITERATIONS = 1024;
  private static final int KEY_LENGTH_BITS = 128;

  private static final HexFormat HEX = HexFormat.of();

  private static final SecretKeySpec AES_KEY = deriveAesKey();

  private LegacyCrypto() {}

  /** The 16 raw bytes stored (serialized) in {@code JukeANator.CTL} for the given tier. */
  public static byte[] licenseKeyBytes(LicenseTier tier, String machineId) {
    if (tier.getDelimiter() == null) {
      throw new IllegalArgumentException("Tier " + tier + " has no license key");
    }
    return md5(tier.getDelimiter() + machineId + tier.getDelimiter());
  }

  public static byte[] md5(String text) {
    try {
      return MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.ISO_8859_1));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("MD5 unavailable", e);
    }
  }

  /** Legacy {@code CryptoUtils.SHA1}: SHA-1 over ISO-8859-1 bytes, lowercase hex. */
  public static String sha1Hex(String text) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-1")
          .digest(text.getBytes(StandardCharsets.ISO_8859_1));
      return HEX.formatHex(digest);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("SHA-1 unavailable", e);
    }
  }

  /** The derived AES key (exposed for tests). */
  public static SecretKeySpec aesKey() {
    return AES_KEY;
  }

  /**
   * Legacy {@code EncryptDecrypt.encryptText}: the empty string passes through unchanged; anything
   * else becomes lowercase hex of AES/ECB/PKCS5 ciphertext.
   */
  public static String encryptHex(String plainText) {
    if (plainText == null || plainText.isEmpty()) {
      return plainText;
    }
    return encryptHexStrict(plainText);
  }

  /**
   * Legacy {@code CryptoUtils.encryptText}: always encrypts, including the empty string. The
   * server's subscription-check reply uses this variant.
   */
  public static String encryptHexStrict(String plainText) {
    try {
      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE, AES_KEY);
      return HEX.formatHex(cipher.doFinal(plainText.getBytes(StandardCharsets.ISO_8859_1)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES encryption failed", e);
    }
  }

  /** Legacy {@code EncryptDecrypt.decryptText}: the empty string passes through unchanged. */
  public static String decryptHex(String hexText) {
    if (hexText == null || hexText.isEmpty()) {
      return hexText;
    }
    try {
      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
      cipher.init(Cipher.DECRYPT_MODE, AES_KEY);
      return new String(cipher.doFinal(HEX.parseHex(hexText.trim())),
          StandardCharsets.ISO_8859_1);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("AES decryption failed", e);
    }
  }

  private static SecretKeySpec deriveAesKey() {
    try {
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      PBEKeySpec spec = new PBEKeySpec(PASSWORD, SALT, ITERATIONS, KEY_LENGTH_BITS);
      byte[] encoded = factory.generateSecret(spec).getEncoded();
      return new SecretKeySpec(encoded, "AES");
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not derive legacy AES key", e);
    }
  }
}
