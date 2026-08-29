package com.djt.jukeanator_engine.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.io.ClassPathResource;

/**
 * On a brand-new install, seeds a {@code config/application.yml} next to the running WAR -- with
 * {@code app.data-dir} pre-pointed at a sibling {@code data/} directory -- so an end user can
 * customize settings (e.g. {@code spring.datasource.*}) without unpacking or rebuilding the WAR,
 * and so everything the app reads/writes lives under one directory instead of being scattered
 * into {@code user.home}.
 *
 * <p>
 * Never overwrites an existing file, so a customized config survives upgrades and restarts.
 * {@link #seedExternalConfigIfAbsent} always returns the config directory (whether freshly seeded
 * or pre-existing) so the caller can point Spring's config loader at it explicitly -- Spring's own
 * default {@code ./config/} lookup is relative to the process's working directory, which won't
 * match this WAR-relative directory unless the app happens to be launched from inside it.
 *
 * <p>
 * A caller (e.g. a kiosk deployment launched with {@code --app.config-dir=C:\kiosk\config}) can
 * override the WAR-relative default entirely via {@link #seedExternalConfigIfAbsent(Class, Path)}
 * -- the seeded {@code data/} directory then sits alongside the given config directory rather than
 * alongside the WAR.
 */
public final class ExternalConfigInitializer {

  private static final Logger LOG = LoggerFactory.getLogger(ExternalConfigInitializer.class);

  private static final String CONFIG_DIR_NAME = "config";
  private static final String DATA_DIR_NAME = "data";
  private static final String CONFIG_FILE_NAME = "application.yml";

  // Matches the top-level "app:" mapping key so the seeded data-dir can be inserted as its first
  // child, regardless of exactly how the surrounding comments in the bundled template are worded.
  private static final Pattern APP_KEY_LINE = Pattern.compile("(?m)^app:[ \\t]*$");

  private ExternalConfigInitializer() {
  }

  public static Path seedExternalConfigIfAbsent(Class<?> applicationClass) {
    return seedExternalConfigIfAbsent(applicationClass, null);
  }

  /**
   * @param configDirOverride explicit config directory to use instead of the WAR-relative default
   *        (e.g. from a {@code --app.config-dir} program argument), or {@code null} to fall back
   *        to the default {@code <war-dir>/config}
   */
  public static Path seedExternalConfigIfAbsent(Class<?> applicationClass, Path configDirOverride) {

    Path configDir;
    Path dataDir;
    if (configDirOverride != null) {
      configDir = configDirOverride;
      dataDir = configDir.resolveSibling(DATA_DIR_NAME);
    } else {
      Path warDir = new ApplicationHome(applicationClass).getDir().toPath();
      configDir = warDir.resolve(CONFIG_DIR_NAME);
      dataDir = warDir.resolve(DATA_DIR_NAME);
    }
    Path configFile = configDir.resolve(CONFIG_FILE_NAME);

    if (Files.exists(configFile)) {
      return configDir;
    }

    try {
      Files.createDirectories(dataDir);
      Files.createDirectories(configDir);

      String defaultYaml;
      try (var in = new ClassPathResource(CONFIG_FILE_NAME).getInputStream()) {
        defaultYaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }

      // Matcher.replaceFirst() treats backslashes and $ in its replacement argument as its own
      // escape syntax, which silently mangles a raw Windows path (e.g. "C:\kiosk\data" loses both
      // backslashes, becoming "C:kioskdata" -- a drive-relative path Windows then resolves against
      // whatever the process's current directory on that drive happens to be). quoteReplacement()
      // escapes the text first so it's inserted literally.
      String replacement = "app:" + System.lineSeparator() + "  data-dir: " + toYamlScalar(dataDir);
      String seededYaml =
          APP_KEY_LINE.matcher(defaultYaml).replaceFirst(Matcher.quoteReplacement(replacement));

      Files.writeString(configFile, seededYaml, StandardCharsets.UTF_8);

      LOG.info("First run on this machine -- wrote a customizable copy of {} to {}, "
          + "with app.data-dir pointed at {}", CONFIG_FILE_NAME, configFile, dataDir);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not seed external config at " + configFile, e);
    }

    return configDir;
  }

  // Single-quoted YAML scalar: unlike double-quoted, it treats backslashes literally, so a
  // Windows path needs no escaping -- only an embedded ' has to be doubled.
  private static String toYamlScalar(Path path) {
    return "'" + path.toAbsolutePath().toString().replace("'", "''") + "'";
  }
}
