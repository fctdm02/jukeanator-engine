package com.djt.jukeanator_engine.domain.license.legacy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyCtlFilesTest {

  private static final HexFormat HEX = HexFormat.of();

  /** Java serialization header of a byte[16], as found in legacy JukeANator.CTL files. */
  private static final String BYTE_ARRAY_16_HEADER =
      "aced0005" + "7572" + "0002" + "5b42" + "acf317f8060854e0" + "02" + "0000" + "78" + "70"
          + "00000010";

  /** Java serialization header of a String[4], as found in legacy ExtendCode.CTL files. */
  private static final String STRING_ARRAY_4_HEADER = "aced0005" + "7572" + "0013"
      + HEX.formatHex("[Ljava.lang.String;".getBytes()) + "add256e7e91d7b47" + "02" + "0000"
      + "78" + "70" + "00000004";

  @TempDir
  Path dir;

  @Test
  void licenseKey_serializedLayoutMatchesLegacyBytes() {
    byte[] key = LegacyCrypto.licenseKeyBytes(LicenseTier.C, "661793536");
    byte[] file = LegacyCtlFiles.serializeLicenseKey(key);

    assertEquals(43, file.length);
    assertEquals(BYTE_ARRAY_16_HEADER, HEX.formatHex(Arrays.copyOf(file, 27)));
    assertArrayEquals(key, Arrays.copyOfRange(file, 27, 43));
  }

  @Test
  void licenseKey_roundTrip() throws IOException {
    Path file = dir.resolve(LegacyCtlFiles.LICENSE_KEY_FILENAME);
    byte[] key = LegacyCrypto.licenseKeyBytes(LicenseTier.CD, "{ABC}");
    LegacyCtlFiles.writeLicenseKey(file, key);
    assertArrayEquals(key, LegacyCtlFiles.readLicenseKey(file).orElseThrow());
  }

  @Test
  void licenseKey_missingOrGarbage_isEmpty() throws IOException {
    assertTrue(LegacyCtlFiles.readLicenseKey(dir.resolve("missing.ctl")).isEmpty());
    Path garbage = dir.resolve("garbage.ctl");
    Files.write(garbage, new byte[] {1, 2, 3});
    assertTrue(LegacyCtlFiles.readLicenseKey(garbage).isEmpty());
  }

  @Test
  void extendCode_serializedLayoutMatchesLegacyHeader() {
    ExtendCode code = new ExtendCode("4Q2027", 2192, "661793536", "a@b.com");
    byte[] file = LegacyCtlFiles.serializeExtendCode(code);
    String hex = HEX.formatHex(file);

    assertTrue(hex.startsWith(STRING_ARRAY_4_HEADER), hex);
    // First element: TC_STRING (0x74), u16 length 32, then the hex ciphertext of the quarter.
    String first = "74" + "0020" + HEX.formatHex(LegacyCrypto.encryptHex("4Q2027").getBytes());
    assertTrue(hex.startsWith(STRING_ARRAY_4_HEADER + first), hex);
  }

  @Test
  void extendCode_roundTripRestoresLastModified() throws IOException {
    Path file = dir.resolve(LegacyCtlFiles.EXTEND_CODE_FILENAME);
    ExtendCode code = new ExtendCode("1Q2028", 100, "{GUID}", "x@y.com");
    LegacyCtlFiles.writeExtendCode(file, code);

    FileTime original = FileTime.fromMillis(1_600_000_000_000L);
    Files.setLastModifiedTime(file, original);

    LegacyCtlFiles.writeExtendCode(file, code.withHoursRemaining(99));

    assertEquals(code.withHoursRemaining(99), LegacyCtlFiles.readExtendCode(file));
    assertEquals(original, Files.getLastModifiedTime(file));
  }

  @Test
  void extendCode_missingFile_throws() {
    assertThrows(IOException.class, () -> LegacyCtlFiles.readExtendCode(dir.resolve("none")));
  }
}
