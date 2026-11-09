package com.djt.jukeanator_engine.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class LegacyInstallMigratorTest {

  // MD5("PASSWORD"), the legacy default, as stored in the sample AdminPwd.CTL / OwnerPwd.CTL.
  private static final String PASSWORD_MD5 = "319f4d26e3c536b5dd871bb2c52e3178";

  @TempDir
  Path tempDir;

  private Path legacyDir;
  private Path dataDir;
  private Path configFile;
  private Path musicDir;

  @BeforeEach
  void setUp() throws Exception {

    legacyDir = Files.createDirectories(tempDir.resolve("old_kiosk"));
    dataDir = tempDir.resolve("kiosk").resolve("data");
    configFile = Files.createDirectories(tempDir.resolve("kiosk").resolve("config"))
        .resolve("application.yml");
    musicDir = Files.createDirectories(legacyDir.resolve("Music"));

    Files.writeString(configFile, """
        app:
          data-dir: 'C:\\kiosk\\data'
        user-interface:
          enable-hibernation: false
          hibernate-begin: 3
          increment-credits-key: a
          credits-per-dollar: 3
        license:
          customer-email: ~
        """);
  }

  private void writeProperties(String scanPaths) throws Exception {

    Files.writeString(legacyDir.resolve(LegacyInstallMigrator.PROPERTIES_FILE), String.join("\r\n",
        "JukeANator Properties (Build: 06-27-2019)",
        "-----------------------",
        "AlwaysOnTop=true",
        "UseBigScrollBars=false",
        "EnableHibernation=true",
        "HibernateBegin=2",
        "HibernateEnd=11",
        "UnlimitedCredits=false",
        "CreditsPer=4",
        "credits=7",
        "BonusFactor_0=0",
        "BonusFactor_1=6",
        "BonusFactor_2=12",
        "AcceptorIncrementOnesKey=Q",
        "PlayExplicitSongs=false",
        "AllowExplicitLyricsBegin=22",
        "AllowExplicitLyricsEnd=4",
        "MinimumMinutesBetweenSongPlays=45",
        "SongPlayer=com.djt.jukeanator.player.VlcMgr",
        "WinampLocation=winamp.exe",
        "ScanPaths=" + scanPaths,
        "CustomerEmail=bar@example.com",
        "LocationName=Joe's Bar",
        "AutoProvisionEmail=",
        "PlayerVolume=not-a-number",
        ""));
  }

  private static byte[] serialize(byte[] bytes) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
      oos.writeObject(bytes);
    }
    return out.toByteArray();
  }

  // Windows only: relative CDStats paths are joined with '\' under a Windows scan folder.
  @Test
  @EnabledOnOs(OS.WINDOWS)
  void fullMigration() throws Exception {

    writeProperties(musicDir + "," + tempDir.resolve("Other"));

    Files.write(legacyDir.resolve("JukeANator.CTL"), new byte[] {1, 2, 3});
    Files.write(legacyDir.resolve("JukeANator2.CTL"), new byte[] {4});
    Path extendCode = legacyDir.resolve("ExtendCode.CTL");
    Files.write(extendCode, new byte[] {5, 6});
    FileTime extendCodeTime = FileTime.fromMillis(1_500_000_000_000L);
    Files.setLastModifiedTime(extendCode, extendCodeTime);

    byte[] passwordFile = serialize(HexFormat.of().parseHex(PASSWORD_MD5));
    assertEquals(43, passwordFile.length, "same layout as the legacy sample files");
    Files.write(legacyDir.resolve("AdminPwd.CTL"), passwordFile);
    Files.write(legacyDir.resolve("OwnerPwd.CTL"), passwordFile);

    Files.writeString(legacyDir.resolve("CDStats.TXT"), String.join("\r\n",
        "     3  1892     2     " + musicDir + "\\80s\\Song A.mp3",
        "     0    10     0     80s\\Song B.mp3 ISRC:\"US123\" ArtistDisplayName:\"X\"",
        "garbage",
        ""));

    assertTrue(LegacyInstallMigrator.migrateIfNeeded(legacyDir, dataDir, configFile));

    // Data files
    assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(dataDir.resolve("JukeANator.CTL")));
    assertArrayEquals(new byte[] {4}, Files.readAllBytes(dataDir.resolve("JukeANator2.CTL")));
    assertEquals(extendCodeTime,
        Files.getLastModifiedTime(dataDir.resolve("ExtendCode.CTL")));

    // CDStats
    assertEquals(List.of("3 " + musicDir + "\\80s\\Song A.mp3", "0 " + musicDir + "\\80s\\Song B.mp3"),
        Files.readAllLines(dataDir.resolve("CDStats.TXT")));
    assertTrue(Files.exists(dataDir.resolve("CDStats_legacy.TXT")));

    // Passwords
    assertEquals(PASSWORD_MD5, Files.readString(dataDir.resolve("admin.md5")));
    assertEquals(PASSWORD_MD5, Files.readString(dataDir.resolve("owner.md5")));

    // Pending scan: first ScanPaths entry only
    assertEquals(musicDir.toString(),
        Files.readString(dataDir.resolve(LegacyInstallMigrator.PENDING_SCAN_FILE)));

    // Settings
    Map<String, Map<String, Object>> yaml = new Yaml().load(Files.readString(configFile));
    Map<String, Object> ui = yaml.get("user-interface");
    assertEquals("true", ui.get("enable-hibernation"));
    assertEquals("2", ui.get("hibernate-begin"));
    assertEquals("11", ui.get("hibernate-end"));
    assertEquals("q", ui.get("increment-credits-key"));
    assertEquals("4", ui.get("credits-per-dollar"));
    assertEquals("6", ui.get("five-dollar-bonus-credits"));
    assertEquals("12", ui.get("ten-dollar-bonus-credits"));
    assertEquals("7", ui.get("num-credits"));
    assertEquals("true", ui.get("always-on-top"));
    assertEquals("false", ui.get("use-big-scrollbars"));
    Map<String, Object> queue = yaml.get("song-queue");
    assertEquals("false", queue.get("allow-explicit-songs-at-all-times"));
    assertEquals("22", queue.get("allow-explicit-songs-begin"));
    assertEquals("45", queue.get("minimum-minutes-between-song-plays"));
    Map<String, Object> player = yaml.get("song-player");
    assertEquals("vlc", player.get("player-type"));
    assertFalse(player.containsKey("player-volume"), "unparseable values are skipped");
    assertFalse(player.containsKey("winamp-exe-path"), "relative Winamp paths are skipped");
    Map<String, Object> license = yaml.get("license");
    assertEquals("bar@example.com", license.get("customer-email"));
    assertEquals("Joe's Bar", license.get("location-name"));
    assertFalse(license.containsKey("auto-provision-email"), "blank values are skipped");
    // Unrelated settings survive, including the backslash path.
    assertEquals("C:\\kiosk\\data", ((Map<?, ?>) yaml.get("app")).get("data-dir"));

    assertTrue(Files.exists(dataDir.resolve(LegacyInstallMigrator.DONE_MARKER)));
  }

  @Test
  void runsOnlyOnce_andNeverOverwritesExistingFiles() throws Exception {

    writeProperties(musicDir.toString());
    Files.write(legacyDir.resolve("JukeANator.CTL"), new byte[] {1});
    Files.write(legacyDir.resolve("AdminPwd.CTL"),
        serialize(HexFormat.of().parseHex(PASSWORD_MD5)));
    Files.writeString(legacyDir.resolve("CDStats.TXT"), "1 0 0     C:\\x.mp3\r\n");

    Files.createDirectories(dataDir);
    Files.write(dataDir.resolve("JukeANator.CTL"), new byte[] {9});
    Files.writeString(dataDir.resolve("admin.sha"), "existing");
    Files.writeString(dataDir.resolve("CDStats.TXT"), "existing");

    assertTrue(LegacyInstallMigrator.migrateIfNeeded(legacyDir, dataDir, configFile));

    assertArrayEquals(new byte[] {9}, Files.readAllBytes(dataDir.resolve("JukeANator.CTL")));
    assertFalse(Files.exists(dataDir.resolve("admin.md5")));
    assertEquals("existing", Files.readString(dataDir.resolve("CDStats.TXT")));

    String configAfterFirstRun = Files.readString(configFile);
    Files.writeString(legacyDir.resolve(LegacyInstallMigrator.PROPERTIES_FILE), "CreditsPer=1\r\n");
    assertFalse(LegacyInstallMigrator.migrateIfNeeded(legacyDir, dataDir, configFile));
    assertEquals(configAfterFirstRun, Files.readString(configFile));
  }

  @Test
  void noLegacyInstall_doesNothing() {

    assertFalse(LegacyInstallMigrator.migrateIfNeeded(legacyDir, dataDir, configFile));
    assertFalse(Files.exists(dataDir));
  }

  // Windows only: relative CDStats paths are joined with '\' under a Windows scan folder.
  @Test
  @EnabledOnOs(OS.WINDOWS)
  void zippedCdStats_isUnzipped() throws Exception {

    writeProperties(musicDir.toString());
    ByteArrayOutputStream zipped = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(zipped)) {
      zip.putNextEntry(new ZipEntry("CDStats.TXT"));
      zip.write("     8     1     0     Rock\\Song.mp3\r\n".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    Files.write(legacyDir.resolve("CDStats.TXT"), zipped.toByteArray());

    LegacyInstallMigrator.migrateIfNeeded(legacyDir, dataDir, configFile);

    assertEquals(List.of("8 " + musicDir + "\\Rock\\Song.mp3"),
        Files.readAllLines(dataDir.resolve("CDStats.TXT")));
  }

  @Test
  void missingScanFolder_recordsNoPendingScan() throws Exception {

    writeProperties(tempDir.resolve("does-not-exist").toString());

    LegacyInstallMigrator.migrateIfNeeded(legacyDir, dataDir, configFile);

    assertFalse(Files.exists(dataDir.resolve(LegacyInstallMigrator.PENDING_SCAN_FILE)));
  }

  @Test
  void takePendingScanPath_returnsOnce() throws Exception {

    Files.createDirectories(dataDir);
    Files.writeString(dataDir.resolve(LegacyInstallMigrator.PENDING_SCAN_FILE),
        musicDir.toString());

    assertEquals(musicDir.toString(), LegacyInstallMigrator.takePendingScanPath(dataDir));
    assertNull(LegacyInstallMigrator.takePendingScanPath(dataDir));
  }

  @Test
  void readLegacyProperties_keepsBackslashesAndSkipsHeaderLines() throws Exception {

    writeProperties("C:\\kiosk\\Demo_Music");

    Map<String, String> properties = LegacyInstallMigrator
        .readLegacyProperties(legacyDir.resolve(LegacyInstallMigrator.PROPERTIES_FILE));

    assertEquals("C:\\kiosk\\Demo_Music", properties.get("ScanPaths"));
    assertFalse(properties.containsKey("-----------------------"));
    assertEquals("", properties.get("AutoProvisionEmail"));
  }

  @Test
  void parseScanPaths_splitsCommasAndExpandsDriveLetters() {

    assertEquals(List.of("C:\\Music", "E:\\", "D:\\More"),
        LegacyInstallMigrator.parseScanPaths("C:\\Music, E ,,D:\\More"));
  }

  @Test
  void convertCdStatsLine_handlesNewFormatAndTrailingFields() {

    assertEquals("4 C:\\Music\\a.mp3",
        LegacyInstallMigrator.convertCdStatsLine("4 C:\\Music\\a.mp3", null));
    assertEquals("2 C:\\Music\\b.mp3",
        LegacyInstallMigrator.convertCdStatsLine(
            "     2     5     1     b.mp3 SongDisplayName:\"B\"", "C:\\Music\\"));
    assertNull(LegacyInstallMigrator.convertCdStatsLine("no numbers here", null));
  }

  @Test
  void resolveDataDir_prefersArgumentThenConfigFile() {

    assertEquals(Path.of("D:\\data"), LegacyInstallMigrator.resolveDataDir("D:\\data", configFile));
    assertEquals(Path.of("C:\\kiosk\\data"), LegacyInstallMigrator.resolveDataDir(null, configFile));
  }
}
