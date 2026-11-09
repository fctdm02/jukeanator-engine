package com.djt.jukeanator_engine.domain.license.legacy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;

/**
 * Golden values were computed independently (legacy {@code CryptoUtils.main} output and .NET
 * PBKDF2/AES/MD5), so these tests pin the port to the legacy bytes rather than to itself.
 */
class LegacyCryptoTest {

  private static final HexFormat HEX = HexFormat.of();

  @Test
  void sha1Hex_matchesLegacyCryptoUtilsMainOutput() {
    assertEquals("d6b2818523532fe7169b182adb8bd9f1d2061588", LegacyCrypto.sha1Hex("jukeanator22"));
    assertEquals("188de99c818c31ed8a8530beaea542857e16c4a2", LegacyCrypto.sha1Hex("volare14"));
  }

  @Test
  void aesKey_matchesIndependentPbkdf2Computation() {
    assertEquals("6f018ccd57a6f1d5757a13674a75c1c2",
        HEX.formatHex(LegacyCrypto.aesKey().getEncoded()));
  }

  @Test
  void encryptHex_matchesIndependentAesEcbComputation() {
    assertEquals("cfb936baf6ba25da12afe4e8617996e9", LegacyCrypto.encryptHex("4Q2027"));
    assertEquals("b5613dad188f727f693b229f5882daff", LegacyCrypto.encryptHex("6617935364Q2027"));
    assertEquals("4Q2027", LegacyCrypto.decryptHex("cfb936baf6ba25da12afe4e8617996e9"));
  }

  @Test
  void encryptHex_matchesLegacyOracle() {
    for (String s : new String[] {"4Q2027", "2192", "%7B1D38215A-A137%7D3Q2026",
        "thomas.myers1@gmail.com", "00-1A-70-F9-9D-DE"}) {
      assertEquals(LegacyCryptoOracle.encryptText(s), LegacyCrypto.encryptHex(s));
      assertEquals(s, LegacyCryptoOracle.decryptText(LegacyCrypto.encryptHex(s)));
    }
  }

  @Test
  void emptyString_passesThroughLikeEncryptDecrypt_butStrictVariantEncrypts() {
    assertEquals("", LegacyCrypto.encryptHex(""));
    assertEquals("", LegacyCrypto.decryptHex(""));
    assertEquals(LegacyCryptoOracle.encryptText(""), LegacyCrypto.encryptHexStrict(""));
  }

  @Test
  void licenseKeyBytes_isMd5OfDelimitedMachineId() {
    assertArrayEquals(HEX.parseHex("200431d44884f5b1d464155ec840f715"),
        LegacyCrypto.licenseKeyBytes(LicenseTier.C, "661793536"));
    assertArrayEquals(LegacyCrypto.md5("__661793536__"),
        LegacyCrypto.licenseKeyBytes(LicenseTier.CD, "661793536"));
  }
}
