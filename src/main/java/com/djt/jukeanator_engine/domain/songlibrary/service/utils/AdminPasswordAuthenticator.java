package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks a JFC/Swing Admin panel password against the account's hash file in the data dir.
 *
 * <ul>
 * <li>{@code <account>.sha}: SHA-256 of the password (UTF-8), lowercase hex. The normal format.</li>
 * <li>{@code <account>.md5}: written by the legacy install migration from {@code AdminPwd.CTL} /
 * {@code OwnerPwd.CTL}. The legacy app hashed the upper-cased password with MD5, so the match is
 * case-insensitive. On the first successful login it is replaced by a {@code .sha} file of the
 * password exactly as typed, and from then on the password is case-sensitive.</li>
 * </ul>
 */
public final class AdminPasswordAuthenticator {

  private static final Logger LOG = LoggerFactory.getLogger(AdminPasswordAuthenticator.class);

  public static final String SHA_SUFFIX = ".sha";
  public static final String LEGACY_MD5_SUFFIX = ".md5";

  private static final HexFormat HEX = HexFormat.of();

  private AdminPasswordAuthenticator() {}

  /**
   * @param dataDir the data dir holding the hash files
   * @param accountName the account's file base name, e.g. {@code admin} or {@code owner}
   * @param password the password as typed
   * @return true if the password matches; false if it doesn't or the account has no hash file
   */
  public static boolean authenticate(Path dataDir, String accountName, String password)
      throws IOException {

    Path shaFile = dataDir.resolve(accountName + SHA_SUFFIX);
    if (Files.exists(shaFile)) {
      String storedHash = Files.readString(shaFile, StandardCharsets.UTF_8).trim();
      return sha256Hex(password).equalsIgnoreCase(storedHash);
    }

    Path md5File = dataDir.resolve(accountName + LEGACY_MD5_SUFFIX);
    if (Files.exists(md5File)) {
      String storedHash = Files.readString(md5File, StandardCharsets.UTF_8).trim();
      if (!legacyMd5Hex(password).equalsIgnoreCase(storedHash)) {
        return false;
      }
      Files.writeString(shaFile, sha256Hex(password), StandardCharsets.UTF_8);
      Files.delete(md5File);
      LOG.info("Upgraded the migrated legacy {} password to {}", accountName, shaFile);
      return true;
    }

    return false;
  }

  public static String sha256Hex(String password) {
    return HEX.formatHex(digest("SHA-256", password.getBytes(StandardCharsets.UTF_8)));
  }

  // Matches the legacy LogonDialog: MD5 of the upper-cased password.
  static String legacyMd5Hex(String password) {
    return HEX.formatHex(
        digest("MD5", password.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
  }

  private static byte[] digest(String algorithm, byte[] bytes) {
    try {
      return MessageDigest.getInstance(algorithm).digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(algorithm + " unavailable", e);
    }
  }
}
