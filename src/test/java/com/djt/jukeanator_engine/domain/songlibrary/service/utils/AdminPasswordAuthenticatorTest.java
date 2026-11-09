package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdminPasswordAuthenticatorTest {

  // MD5("PASSWORD"), as found in the legacy sample AdminPwd.CTL / OwnerPwd.CTL.
  private static final String LEGACY_PASSWORD_MD5 = "319f4d26e3c536b5dd871bb2c52e3178";

  @Test
  void shaFile_matchesExactPasswordOnly(@TempDir Path dataDir) throws Exception {

    Files.writeString(dataDir.resolve("admin.sha"), AdminPasswordAuthenticator.sha256Hex("Secret"));

    assertTrue(AdminPasswordAuthenticator.authenticate(dataDir, "admin", "Secret"));
    assertFalse(AdminPasswordAuthenticator.authenticate(dataDir, "admin", "secret"));
  }

  @Test
  void noHashFile_fails(@TempDir Path dataDir) throws Exception {

    assertFalse(AdminPasswordAuthenticator.authenticate(dataDir, "owner", "anything"));
  }

  @Test
  void legacyMd5_isCaseInsensitive_andUpgradesToShaOnSuccess(@TempDir Path dataDir)
      throws Exception {

    Path md5File = dataDir.resolve("owner.md5");
    Path shaFile = dataDir.resolve("owner.sha");
    Files.writeString(md5File, LEGACY_PASSWORD_MD5);

    assertFalse(AdminPasswordAuthenticator.authenticate(dataDir, "owner", "wrong"));
    assertTrue(Files.exists(md5File), "a failed login must not upgrade");

    assertTrue(AdminPasswordAuthenticator.authenticate(dataDir, "owner", "password"));
    assertFalse(Files.exists(md5File));
    assertEquals(AdminPasswordAuthenticator.sha256Hex("password"), Files.readString(shaFile));

    // From now on the password is the exact text typed on that first login.
    assertTrue(AdminPasswordAuthenticator.authenticate(dataDir, "owner", "password"));
    assertFalse(AdminPasswordAuthenticator.authenticate(dataDir, "owner", "PASSWORD"));
  }

  @Test
  void legacyMd5Hex_hashesUpperCasedPassword() {

    assertEquals(LEGACY_PASSWORD_MD5, AdminPasswordAuthenticator.legacyMd5Hex("PASSWORD"));
    assertEquals(LEGACY_PASSWORD_MD5, AdminPasswordAuthenticator.legacyMd5Hex("password"));
  }
}
