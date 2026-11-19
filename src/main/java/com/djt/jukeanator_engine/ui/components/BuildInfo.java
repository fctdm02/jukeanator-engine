package com.djt.jukeanator_engine.ui.components;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;

/**
 * The application's build date, for display. Taken from {@code META-INF/build-info.properties}
 * ({@code build.time}, written by the {@code spring-boot-maven-plugin} {@code build-info} goal);
 * when that is missing (e.g. running from the IDE), the last-modified time of the running jar or of
 * this class file is used instead.
 */
final class BuildInfo {

  private static final DateTimeFormatter DISPLAY =
      DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US);

  private static volatile String cached;

  private BuildInfo() {
  }

  /** The build date formatted for display, or {@code "unknown"}. */
  static String buildDate() {
    String value = cached;
    if (value == null) {
      Instant instant = fromBuildInfo();
      if (instant == null) {
        instant = fromCodeLocation();
      }
      value = instant == null ? "unknown" : DISPLAY.withZone(ZoneId.systemDefault()).format(instant);
      cached = value;
    }
    return value;
  }

  private static Instant fromBuildInfo() {
    try (InputStream in =
        BuildInfo.class.getClassLoader().getResourceAsStream("META-INF/build-info.properties")) {
      if (in == null) {
        return null;
      }
      Properties props = new Properties();
      props.load(in);
      String time = props.getProperty("build.time");
      return time == null || time.isBlank() ? null : Instant.parse(time.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private static Instant fromCodeLocation() {
    try {
      var source = BuildInfo.class.getProtectionDomain().getCodeSource();
      if (source != null && source.getLocation() != null
          && "file".equals(source.getLocation().getProtocol())) {
        Path path = Path.of(source.getLocation().toURI());
        if (Files.isDirectory(path)) {
          // Exploded classes (IDE): use this class file's compile time.
          path = path.resolve(BuildInfo.class.getName().replace('.', '/') + ".class");
        }
        if (Files.isRegularFile(path)) {
          return Files.getLastModifiedTime(path).toInstant();
        }
      }
    } catch (Exception e) {
      // Unknown.
    }
    return null;
  }
}
