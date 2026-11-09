package com.djt.jukeanator_engine.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.AdminPasswordAuthenticator;

/**
 * One-time migration of a legacy JukeANator install (by default {@code C:\old_kiosk}, the folder an
 * operator renames the old {@code C:\kiosk} to before installing this app) into this app's data
 * dir and external {@code application.yml}. Runs from {@code main()} before Spring starts, so the
 * copied license files, play counts and settings are all in place for this same start.
 *
 * <p>It runs when the legacy dir holds a {@code JukeANator.properties} and the data dir has no
 * {@value #DONE_MARKER} yet. Each step is independent: a failure is logged and the remaining steps
 * still run. The marker is written at the end either way, so a partial migration is not retried on
 * every start (delete the marker to run it again). Existing files in the data dir are never
 * overwritten.
 *
 * <ol>
 * <li>Copies {@code JukeANator.CTL} ({@code JukeANator1.CTL}..{@code JukeANator10.CTL}) and
 * {@code ExtendCode.CTL}, and converts {@code CDStats.TXT} into the format the song library
 * restores from.</li>
 * <li>Converts {@code AdminPwd.CTL} / {@code OwnerPwd.CTL} (MD5 of the upper-cased password) into
 * {@code admin.md5} / {@code owner.md5}; see {@link AdminPasswordAuthenticator}.</li>
 * <li>Records the first {@code ScanPaths} folder in {@value #PENDING_SCAN_FILE}; the JFC/Swing UI
 * scans it on startup instead of asking for a music folder.</li>
 * <li>Copies the directly applicable {@code JukeANator.properties} settings into
 * {@code application.yml}.</li>
 * </ol>
 */
public final class LegacyInstallMigrator {

  private static final Logger LOG = LoggerFactory.getLogger(LegacyInstallMigrator.class);

  public static final Path DEFAULT_LEGACY_DIR = Path.of("C:\\old_kiosk");
  public static final String DONE_MARKER = "LegacyMigration.DONE";
  public static final String PENDING_SCAN_FILE = "LegacyMigration.SCAN";

  static final String PROPERTIES_FILE = "JukeANator.properties";
  static final String LICENSE_KEY_FILE = "JukeANator.CTL";
  static final String EXTEND_CODE_FILE = "ExtendCode.CTL";
  static final String CD_STATS_FILE = "CDStats.TXT";
  static final String CD_STATS_LEGACY_COPY = "CDStats_legacy.TXT";
  static final String ADMIN_PASSWORD_FILE = "AdminPwd.CTL";
  static final String OWNER_PASSWORD_FILE = "OwnerPwd.CTL";

  private static final int MAX_NUMBERED_LICENSE_FILES = 10;
  private static final int MAX_MIGRATED_CREDITS = 9999;

  // Legacy CDStats line: three right-aligned numbers (paid plays, CD age, free plays), then the
  // song path. Older/newer files may have only the play count before the path.
  private static final Pattern CD_STATS_LEGACY_LINE =
      Pattern.compile("^\\s*(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+(.+)$");
  private static final Pattern CD_STATS_SHORT_LINE = Pattern.compile("^\\s*(-?\\d+)\\s+(.+)$");
  private static final String[] CD_STATS_TRAILING_FIELDS =
      {" ISRC:\"", " ArtistDisplayName:\"", " TrackDisplayName:\"", " SongDisplayName:\""};
  private static final Pattern ABSOLUTE_PATH = Pattern.compile("^([A-Za-z]:|[\\\\/]).*");

  private LegacyInstallMigrator() {}

  /**
   * Runs the migration if a legacy install is present and it hasn't run before.
   *
   * @param legacyDir the legacy install dir, e.g. {@code C:\old_kiosk}
   * @param dataDir this app's {@code app.data-dir}
   * @param configFile the external {@code application.yml} to write settings into
   * @return true if the migration ran
   */
  public static boolean migrateIfNeeded(Path legacyDir, Path dataDir, Path configFile) {

    if (legacyDir == null || !Files.isRegularFile(legacyDir.resolve(PROPERTIES_FILE))) {
      return false;
    }
    if (Files.exists(dataDir.resolve(DONE_MARKER))) {
      return false;
    }

    LOG.info("Legacy install found at {} -- migrating its data into {} and settings into {}",
        legacyDir, dataDir, configFile);

    List<String> results = new ArrayList<>();
    try {
      Files.createDirectories(dataDir);
    } catch (IOException e) {
      LOG.error("Legacy migration skipped: could not create data dir {}", dataDir, e);
      return false;
    }

    Map<String, String> properties = Map.of();
    try {
      properties = readLegacyProperties(legacyDir.resolve(PROPERTIES_FILE));
    } catch (Exception e) {
      LOG.error("Could not read legacy {}", PROPERTIES_FILE, e);
      results.add("properties: FAILED " + e);
    }
    List<String> scanPaths = parseScanPaths(properties.get("ScanPaths"));
    String firstScanPath = scanPaths.isEmpty() ? null : scanPaths.get(0);

    runStep("data files", results, () -> copyDataFiles(legacyDir, dataDir));
    runStep("CDStats", results, () -> convertCdStats(legacyDir, dataDir, firstScanPath));
    runStep("passwords", results, () -> migratePasswords(legacyDir, dataDir));
    runStep("scan path", results, () -> recordPendingScan(scanPaths, dataDir));
    Map<String, String> finalProperties = properties;
    runStep("settings", results, () -> migrateSettings(finalProperties, configFile));

    try {
      List<String> marker = new ArrayList<>();
      marker.add("Migrated from " + legacyDir + " at " + LocalDateTime.now());
      marker.addAll(results);
      Files.write(dataDir.resolve(DONE_MARKER), marker, StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOG.error("Could not write {}; the legacy migration will run again next start", DONE_MARKER,
          e);
    }
    LOG.info("Legacy migration finished: {}", results);
    return true;
  }

  /**
   * Resolves {@code app.data-dir} before Spring is up: the {@code --app.data-dir} program argument
   * if given, else the value in the external {@code application.yml}, else the same default as
   * {@code AppProperties} ({@code <user.home>/.jukeanator}).
   */
  public static Path resolveDataDir(String dataDirArgument, Path configFile) {

    if (dataDirArgument != null && !dataDirArgument.isBlank()) {
      return Path.of(dataDirArgument);
    }
    try {
      if (configFile != null && Files.isRegularFile(configFile)) {
        Object root = new Yaml(new LoaderOptions())
            .load(Files.readString(configFile, StandardCharsets.UTF_8));
        if (root instanceof Map<?, ?> rootMap && rootMap.get("app") instanceof Map<?, ?> app) {
          Object value = app.get("data-dir") != null ? app.get("data-dir") : app.get("dataDir");
          if (value != null && !value.toString().isBlank()) {
            return Path.of(value.toString());
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Could not read app.data-dir from {}; using the default", configFile, e);
    }
    return Path.of(System.getProperty("user.home"), ".jukeanator");
  }

  /**
   * Returns the music folder the migration asked to scan, removing the request so it is only acted
   * on once, or null if there is none or the folder no longer exists.
   */
  public static String takePendingScanPath(Path dataDir) {

    Path pendingScanFile = dataDir.resolve(PENDING_SCAN_FILE);
    if (!Files.exists(pendingScanFile)) {
      return null;
    }
    try {
      String scanPath = Files.readString(pendingScanFile, StandardCharsets.UTF_8).trim();
      Files.deleteIfExists(pendingScanFile);
      if (scanPath.isEmpty() || !Files.isDirectory(Path.of(scanPath))) {
        LOG.warn("Legacy scan folder [{}] does not exist; asking for a music folder instead",
            scanPath);
        return null;
      }
      return scanPath;
    } catch (Exception e) {
      LOG.warn("Could not read {}", pendingScanFile, e);
      return null;
    }
  }

  // STEP 1: DATA FILES

  private static String copyDataFiles(Path legacyDir, Path dataDir) throws IOException {

    List<String> fileNames = new ArrayList<>();
    fileNames.add(LICENSE_KEY_FILE);
    for (int i = 1; i <= MAX_NUMBERED_LICENSE_FILES; i++) {
      fileNames.add("JukeANator" + i + ".CTL");
    }
    fileNames.add(EXTEND_CODE_FILE);

    List<String> copied = new ArrayList<>();
    for (String fileName : fileNames) {
      Path source = legacyDir.resolve(fileName);
      Path target = dataDir.resolve(fileName);
      if (!Files.isRegularFile(source)) {
        continue;
      }
      if (Files.exists(target)) {
        LOG.info("Not copying legacy {}: {} already exists", fileName, target);
        continue;
      }
      // COPY_ATTRIBUTES keeps the last-modified time, which the ExtendCode.CTL checks rely on.
      Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
      copied.add(fileName);
    }
    return "copied " + copied;
  }

  private static String convertCdStats(Path legacyDir, Path dataDir, String firstScanPath)
      throws IOException {

    Path source = legacyDir.resolve(CD_STATS_FILE);
    Path target = dataDir.resolve(CD_STATS_FILE);
    if (!Files.isRegularFile(source)) {
      return "no legacy " + CD_STATS_FILE;
    }
    if (Files.exists(target)) {
      return "skipped, " + target + " already exists";
    }

    byte[] raw = Files.readAllBytes(source);
    Path legacyCopy = dataDir.resolve(CD_STATS_LEGACY_COPY);
    if (!Files.exists(legacyCopy)) {
      Files.write(legacyCopy, raw);
    }

    List<String> converted = new ArrayList<>();
    int unparsed = 0;
    for (String line : decode(unzipIfNeeded(raw)).split("\\r?\\n")) {
      if (line.isBlank()) {
        continue;
      }
      String convertedLine = convertCdStatsLine(line, firstScanPath);
      if (convertedLine == null) {
        unparsed++;
      } else {
        converted.add(convertedLine);
      }
    }
    Files.write(target, String.join("\r\n", converted).concat("\r\n")
        .getBytes(StandardCharsets.UTF_8));
    if (unparsed > 0) {
      LOG.warn("Skipped {} unreadable line(s) of legacy {}", unparsed, CD_STATS_FILE);
    }
    return "converted " + converted.size() + " song(s), skipped " + unparsed;
  }

  /**
   * Converts one legacy CDStats line ({@code <paid plays> <age> <free plays>     <path>[ ISRC:"…"]})
   * into the {@code <plays> <absolute path>} line the song library restores from. A path relative
   * to the legacy scan root is made absolute against {@code firstScanPath}.
   *
   * @return the converted line, or null if the line could not be parsed
   */
  static String convertCdStatsLine(String line, String firstScanPath) {

    Matcher matcher = CD_STATS_LEGACY_LINE.matcher(line);
    String plays;
    String path;
    if (matcher.matches()) {
      plays = matcher.group(1);
      path = matcher.group(4);
    } else {
      matcher = CD_STATS_SHORT_LINE.matcher(line);
      if (!matcher.matches()) {
        return null;
      }
      plays = matcher.group(1);
      path = matcher.group(2);
    }

    for (String field : CD_STATS_TRAILING_FIELDS) {
      int index = path.indexOf(field);
      if (index >= 0) {
        path = path.substring(0, index);
      }
    }
    path = path.trim();
    if (path.isEmpty()) {
      return null;
    }

    if (!ABSOLUTE_PATH.matcher(path).matches() && firstScanPath != null) {
      String separator = firstScanPath.contains("/") && !firstScanPath.contains("\\") ? "/" : "\\";
      String root = firstScanPath.endsWith("\\") || firstScanPath.endsWith("/")
          ? firstScanPath.substring(0, firstScanPath.length() - 1)
          : firstScanPath;
      path = root + separator + path;
    }
    return Integer.parseInt(plays) + " " + path;
  }

  // The legacy app zips CDStats.TXT (one entry) when CompressDataFiles=true.
  private static byte[] unzipIfNeeded(byte[] raw) throws IOException {

    if (raw.length < 2 || raw[0] != 'P' || raw[1] != 'K') {
      return raw;
    }
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(raw))) {
      ZipEntry entry = zip.getNextEntry();
      if (entry == null) {
        throw new IOException("Legacy " + CD_STATS_FILE + " is an empty zip file");
      }
      return zip.readAllBytes();
    }
  }

  // STEP 2: PASSWORDS

  private static String migratePasswords(Path legacyDir, Path dataDir) throws IOException {

    List<String> migrated = new ArrayList<>();
    if (migratePassword(legacyDir.resolve(ADMIN_PASSWORD_FILE), dataDir, "admin")) {
      migrated.add("admin");
    }
    if (migratePassword(legacyDir.resolve(OWNER_PASSWORD_FILE), dataDir, "owner")) {
      migrated.add("owner");
    }
    return "migrated " + migrated;
  }

  private static boolean migratePassword(Path legacyFile, Path dataDir, String accountName)
      throws IOException {

    if (!Files.isRegularFile(legacyFile)) {
      return false;
    }
    Path shaFile = dataDir.resolve(accountName + AdminPasswordAuthenticator.SHA_SUFFIX);
    Path md5File = dataDir.resolve(accountName + AdminPasswordAuthenticator.LEGACY_MD5_SUFFIX);
    if (Files.exists(shaFile) || Files.exists(md5File)) {
      LOG.info("Not migrating legacy {}: a password for {} already exists", legacyFile,
          accountName);
      return false;
    }
    byte[] digest = readSerializedMd5Digest(Files.readAllBytes(legacyFile));
    Files.writeString(md5File, HexFormat.of().formatHex(digest), StandardCharsets.UTF_8);
    return true;
  }

  /**
   * Extracts the 16-byte MD5 digest from a legacy password file, which holds it as a
   * Java-serialized {@code byte[]}. Reads the stream layout directly rather than deserializing the
   * file: stream header {@code AC ED}, the {@code [B} array class descriptor, then a 4-byte length
   * (16) and the digest bytes at the end of the file.
   */
  static byte[] readSerializedMd5Digest(byte[] serialized) throws IOException {

    int digestLength = 16;
    int lengthOffset = serialized.length - digestLength - 4;
    if (lengthOffset < 2 || (serialized[0] & 0xFF) != 0xAC || (serialized[1] & 0xFF) != 0xED
        || ByteBuffer.wrap(serialized, lengthOffset, 4).getInt() != digestLength) {
      throw new IOException("Not a legacy MD5 password file");
    }
    byte[] digest = new byte[digestLength];
    System.arraycopy(serialized, serialized.length - digestLength, digest, 0, digestLength);
    return digest;
  }

  // STEP 3: SCAN PATH

  /**
   * Splits legacy {@code ScanPaths} (comma-separated; a lone drive letter like {@code E} means
   * {@code E:\}).
   */
  static List<String> parseScanPaths(String scanPaths) {

    List<String> paths = new ArrayList<>();
    if (scanPaths == null) {
      return paths;
    }
    for (String token : scanPaths.split(",")) {
      String path = token.trim();
      if (path.isEmpty()) {
        continue;
      }
      if (path.length() == 1 && Character.isLetter(path.charAt(0))) {
        path = path + ":\\";
      }
      paths.add(path);
    }
    return paths;
  }

  private static String recordPendingScan(List<String> scanPaths, Path dataDir)
      throws IOException {

    if (scanPaths.isEmpty()) {
      return "no ScanPaths";
    }
    String scanPath = scanPaths.get(0);
    if (scanPaths.size() > 1) {
      LOG.warn("Legacy ScanPaths lists {} folders; only the first, {}, is scanned. Not scanned: {}",
          scanPaths.size(), scanPath, scanPaths.subList(1, scanPaths.size()));
    }
    if (!Files.isDirectory(Path.of(scanPath))) {
      LOG.warn("Legacy scan folder {} does not exist; the app will ask for a music folder",
          scanPath);
      return "scan folder " + scanPath + " does not exist";
    }
    Files.writeString(dataDir.resolve(PENDING_SCAN_FILE), scanPath, StandardCharsets.UTF_8);
    return "scan pending for " + scanPath;
  }

  // STEP 4: SETTINGS

  /**
   * Reads {@code JukeANator.properties} the way the legacy {@code OrderedProperties} did: split each
   * line on its first {@code =}, skip lines without one, and take backslashes literally (which
   * {@link java.util.Properties} would treat as escapes, mangling Windows paths).
   */
  static Map<String, String> readLegacyProperties(Path propertiesFile) throws IOException {

    Map<String, String> properties = new LinkedHashMap<>();
    for (String line : decode(Files.readAllBytes(propertiesFile)).split("\\r?\\n")) {
      int index = line.indexOf('=');
      if (index > 0) {
        properties.put(line.substring(0, index).trim(), line.substring(index + 1).trim());
      }
    }
    return properties;
  }

  private static String migrateSettings(Map<String, String> properties, Path configFile) {

    if (properties.isEmpty()) {
      return "no settings";
    }

    Map<String, Map<String, String>> sections = mapSettings(properties);

    List<String> written = new ArrayList<>();
    for (Map.Entry<String, Map<String, String>> section : sections.entrySet()) {
      if (section.getValue().isEmpty()) {
        continue;
      }
      LOG.info("Migrating legacy settings into {}: {}", section.getKey(), section.getValue());
      if (ExternalConfigUpdater.persistSectionValues(configFile, section.getKey(),
          section.getValue())) {
        written.add(section.getKey() + section.getValue().keySet());
      }
    }
    return "wrote " + written;
  }

  /** Maps legacy property values onto application.yml section/key values. */
  static Map<String, Map<String, String>> mapSettings(Map<String, String> properties) {

    Map<String, String> ui = new LinkedHashMap<>();
    Map<String, String> songQueue = new LinkedHashMap<>();
    Map<String, String> songPlayer = new LinkedHashMap<>();
    Map<String, String> songLibrary = new LinkedHashMap<>();
    Map<String, String> license = new LinkedHashMap<>();

    // Credits / bill acceptor
    String onesKey = properties.get("AcceptorIncrementOnesKey");
    if (onesKey != null && onesKey.length() == 1 && !"*".equals(onesKey)
        && !Character.isWhitespace(onesKey.charAt(0))) {
      ui.put("increment-credits-key", onesKey.toLowerCase(Locale.ROOT));
    }
    putInt(ui, "credits-per-dollar", properties, "CreditsPer");
    // Legacy bonus levels are cumulative per customer session: BonusFactor_1 at $5, _2 at $10.
    putInt(ui, "five-dollar-bonus-credits", properties, "BonusFactor_1");
    putInt(ui, "ten-dollar-bonus-credits", properties, "BonusFactor_2");
    Integer credits = parseInt(properties.get("credits"));
    if (credits != null && credits >= 0 && credits <= MAX_MIGRATED_CREDITS
        && !"true".equalsIgnoreCase(properties.get("UnlimitedCredits"))) {
      ui.put("num-credits", credits.toString());
    }

    // Hibernation / screen saver / display
    putBoolean(ui, "enable-hibernation", properties, "EnableHibernation");
    putInt(ui, "hibernate-begin", properties, "HibernateBegin");
    putInt(ui, "hibernate-end", properties, "HibernateEnd");
    putBoolean(ui, "enable-screen-saver", properties, "UseScreenSaver");
    putBoolean(ui, "always-on-top", properties, "AlwaysOnTop");
    putBoolean(ui, "enable-type-ahead-search", properties, "UseTypeAheadSearch");
    putBoolean(ui, "use-big-scrollbars", properties, "UseBigScrollBars");

    // Song queue rules
    putInt(songQueue, "minimum-minutes-between-song-plays", properties,
        "MinimumMinutesBetweenSongPlays");
    putInt(songQueue, "maximum-consecutive-song-plays-by-artist", properties,
        "MaximumConsecutiveSongPlaysByArtist");
    putBoolean(songQueue, "allow-explicit-songs-at-all-times", properties, "PlayExplicitSongs");
    putInt(songQueue, "allow-explicit-songs-begin", properties, "AllowExplicitLyricsBegin");
    putInt(songQueue, "allow-explicit-songs-end", properties, "AllowExplicitLyricsEnd");

    // Song player
    putInt(songPlayer, "player-volume", properties, "PlayerVolume");
    putInt(songPlayer, "master-volume", properties, "MasterVolume");
    putInt(songPlayer, "line-in-volume", properties, "LineInVolume");
    String playerType = mapPlayerType(properties.get("SongPlayer"));
    if (playerType != null) {
      songPlayer.put("player-type", playerType);
    }
    String winampLocation = properties.get("WinampLocation");
    if (winampLocation != null && !winampLocation.isBlank()) {
      Path winampExe = Path.of(winampLocation);
      if (winampExe.isAbsolute() && Files.isRegularFile(winampExe)) {
        songPlayer.put("winamp-exe-path", winampLocation);
      }
    }

    // Song library
    putBoolean(songLibrary, "use-top-folder-for-genre", properties, "UseTopFoldersForGenre");

    // License
    putNonBlank(license, "customer-email", properties, "CustomerEmail");
    putNonBlank(license, "customer-name", properties, "CustomerName");
    putNonBlank(license, "location-name", properties, "LocationName");
    putNonBlank(license, "auto-provision-email", properties, "AutoProvisionEmail");
    putNonBlank(license, "auto-provision-passcode", properties, "AutoProvisionPasscode");
    putBoolean(license, "demote-to-non-commercial", properties, "DemoteToNonCommercial");

    Map<String, Map<String, String>> sections = new LinkedHashMap<>();
    sections.put("user-interface", ui);
    sections.put("song-queue", songQueue);
    sections.put("song-player", songPlayer);
    sections.put("song-library", songLibrary);
    sections.put("license", license);
    return sections;
  }

  private static String mapPlayerType(String songPlayer) {

    if (songPlayer == null || songPlayer.isBlank()) {
      return null;
    }
    String className = songPlayer.substring(songPlayer.lastIndexOf('.') + 1);
    return switch (className) {
      case "WinAmpMgr" -> "winamp";
      case "VlcMgr" -> "vlc";
      case "DetachedVlcMgr" -> "detached-vlc";
      default -> null;
    };
  }

  private static void putInt(Map<String, String> target, String key,
      Map<String, String> properties, String legacyKey) {

    Integer value = parseInt(properties.get(legacyKey));
    if (value != null) {
      target.put(key, value.toString());
    }
  }

  private static void putBoolean(Map<String, String> target, String key,
      Map<String, String> properties, String legacyKey) {

    String value = properties.get(legacyKey);
    if (value != null && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
      target.put(key, value.toLowerCase(Locale.ROOT));
    }
  }

  private static void putNonBlank(Map<String, String> target, String key,
      Map<String, String> properties, String legacyKey) {

    String value = properties.get(legacyKey);
    if (value != null && !value.isBlank()) {
      target.put(key, value);
    }
  }

  private static Integer parseInt(String value) {

    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  // HELPERS

  // The legacy app wrote its files in the platform charset (UTF-8 when launched by GO.CMD,
  // otherwise usually windows-1252): try UTF-8 strictly, then fall back to ISO-8859-1.
  private static String decode(byte[] bytes) {

    try {
      return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      return new String(bytes, StandardCharsets.ISO_8859_1);
    }
  }

  @FunctionalInterface
  private interface Step {
    String run() throws Exception;
  }

  private static void runStep(String name, List<String> results, Step step) {

    try {
      String result = step.run();
      LOG.info("Legacy migration, {}: {}", name, result);
      results.add(name + ": " + result);
    } catch (Exception e) {
      LOG.error("Legacy migration, {}: FAILED", name, e);
      results.add(name + ": FAILED " + e);
    }
  }
}
