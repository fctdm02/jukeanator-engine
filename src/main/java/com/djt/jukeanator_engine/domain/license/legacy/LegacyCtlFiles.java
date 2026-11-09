package com.djt.jukeanator_engine.domain.license.legacy;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

/**
 * Reads and writes the legacy {@code .CTL} files. Both are plain Java serialization, exactly as
 * the legacy client wrote them, so standard {@link ObjectOutputStream} output is byte-identical:
 *
 * <ul>
 * <li>{@code JukeANator.CTL}: a serialized {@code byte[16]} (43 bytes in total).</li>
 * <li>{@code ExtendCode.CTL}: a serialized {@code String[4]} of hex ciphertexts
 * {quarter, hoursRemaining, machineId, customerEmail}.</li>
 * </ul>
 *
 * <p>Deserialization is restricted to {@code byte[]}, {@code String[]} and {@code String} so a
 * tampered file cannot instantiate arbitrary classes.
 */
public final class LegacyCtlFiles {

  public static final String LICENSE_KEY_FILENAME = "JukeANator.CTL";
  public static final String EXTEND_CODE_FILENAME = "ExtendCode.CTL";

  private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
      "maxdepth=2;maxarray=64;maxbytes=65536;java.lang.String;!*");

  private LegacyCtlFiles() {}

  // ---------------------------------------------------------------- JukeANator.CTL

  /** Returns the 16 key bytes, or empty if the file is missing, empty or not a byte array. */
  public static Optional<byte[]> readLicenseKey(Path file) {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try (InputStream in = Files.newInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(in)) {
      ois.setObjectInputFilter(FILTER);
      Object obj = ois.readObject();
      return obj instanceof byte[] bytes ? Optional.of(bytes) : Optional.empty();
    } catch (IOException | ClassNotFoundException | RuntimeException e) {
      return Optional.empty();
    }
  }

  public static byte[] serializeLicenseKey(byte[] keyBytes) {
    return serialize(keyBytes);
  }

  /** Writes the key atomically (temp file + move) so a crash never leaves a truncated key. */
  public static void writeLicenseKey(Path file, byte[] keyBytes) throws IOException {
    writeAtomically(file, serialize(keyBytes));
  }

  // ---------------------------------------------------------------- ExtendCode.CTL

  /**
   * Reads and decrypts an extend code file.
   *
   * @throws IOException if the file is missing or cannot be decoded
   */
  public static ExtendCode readExtendCode(Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      throw new IOException("Extend code file not found: " + file);
    }
    try (InputStream in = Files.newInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(in)) {
      ois.setObjectInputFilter(FILTER);
      Object obj = ois.readObject();
      if (!(obj instanceof String[] parts) || parts.length < 4) {
        throw new IOException("Unexpected extend code content in " + file);
      }
      String quarter = LegacyCrypto.decryptHex(parts[0]);
      int hours = Integer.parseInt(LegacyCrypto.decryptHex(parts[1]).trim());
      String machineId = LegacyCrypto.decryptHex(parts[2]);
      String email = LegacyCrypto.decryptHex(parts[3]);
      return new ExtendCode(quarter, hours, machineId, email);
    } catch (ClassNotFoundException | RuntimeException e) {
      throw new IOException("Could not decode extend code file " + file, e);
    }
  }

  public static byte[] serializeExtendCode(ExtendCode code) {
    String[] parts = {
        LegacyCrypto.encryptHex(code.expirationQuarter()),
        LegacyCrypto.encryptHex(Integer.toString(code.hoursRemaining())),
        LegacyCrypto.encryptHex(nullToEmpty(code.machineId())),
        LegacyCrypto.encryptHex(nullToEmpty(code.customerEmail()))};
    return serialize(parts);
  }

  /**
   * Writes the extend code and, like the legacy client, restores the file's previous
   * last-modified time so the hourly rewrite is not visible in the file's timestamp.
   */
  public static void writeExtendCode(Path file, ExtendCode code) throws IOException {
    FileTime previous = Files.exists(file) ? Files.getLastModifiedTime(file) : null;
    writeAtomically(file, serializeExtendCode(code));
    if (previous != null) {
      Files.setLastModifiedTime(file, previous);
    }
  }

  // ---------------------------------------------------------------- helpers

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static byte[] serialize(Object obj) {
    try (var bytes = new java.io.ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
      oos.writeObject(obj);
      oos.flush();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Serialization to memory failed", e);
    }
  }

  private static void writeAtomically(Path file, byte[] content) throws IOException {
    Path dir = file.toAbsolutePath().getParent();
    if (dir != null) {
      Files.createDirectories(dir);
    }
    Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
    try {
      try (OutputStream out = Files.newOutputStream(tmp)) {
        out.write(content);
      }
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }
}
