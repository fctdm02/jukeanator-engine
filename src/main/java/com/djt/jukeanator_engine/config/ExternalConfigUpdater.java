package com.djt.jukeanator_engine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * Applies targeted, in-place edits to the external {@code config/application.yml} that
 * {@link ExternalConfigInitializer} seeded (or that a {@code --app.config-dir} override points
 * at), so a setting the app discovers at runtime survives restarts.
 *
 * <p>
 * Edits are line-based rather than a parse/re-serialize round trip, so the user's comments,
 * ordering and formatting elsewhere in the file are left untouched.
 */
public final class ExternalConfigUpdater {

  private static final Logger LOG = LoggerFactory.getLogger(ExternalConfigUpdater.class);

  private static final String CONFIG_FILE_NAME = "application.yml";
  private static final String ADDITIONAL_LOCATION_PROPERTY = "spring.config.additional-location";
  private static final String FILE_PREFIX = "file:";

  private static final String SONG_PLAYER_KEY = "song-player";
  private static final String PLAYER_TYPE_KEY = "player-type";
  private static final String WINAMP_EXE_PATH_KEY = "winamp-exe-path";
  private static final String WINAMP_PLAYER_TYPE = "winamp";

  private static final String DEFAULT_CHILD_INDENT = "  ";

  private static final Pattern SONG_PLAYER_LINE =
      Pattern.compile("^" + SONG_PLAYER_KEY + ":[ \\t]*(#.*)?$");

  // Group 1: indentation, group 2: trailing " # comment" (if any) so it can be preserved.
  private static final Pattern PLAYER_TYPE_LINE =
      Pattern.compile("^([ \\t]+)" + PLAYER_TYPE_KEY + ":[^#]*?([ \\t]+#.*)?$");

  private static final Pattern WINAMP_EXE_PATH_LINE =
      Pattern.compile("^([ \\t]+)" + WINAMP_EXE_PATH_KEY + ":.*$");

  private static final Pattern CHILD_KEY_LINE = Pattern.compile("^([ \\t]+)[^\\s#][^:]*:.*$");

  private ExternalConfigUpdater() {
  }

  /**
   * Resolves the external {@code application.yml} the app was started with, from the
   * {@code spring.config.additional-location} that {@code JukeANatorBackendApplication.main} sets
   * to the (WAR-relative or {@code --app.config-dir}) config directory.
   *
   * @return the config file, or {@code null} if no external config location is set
   */
  public static Path resolveExternalConfigFile(Environment environment) {

    String location = environment.getProperty(ADDITIONAL_LOCATION_PROPERTY);
    if (location == null || location.isBlank()) {
      return null;
    }
    // Only a single location is ever set by main(), but tolerate a comma-separated list.
    String first = location.split(",")[0].trim();
    if (first.startsWith(FILE_PREFIX)) {
      first = first.substring(FILE_PREFIX.length());
    }
    if (first.isEmpty()) {
      return null;
    }
    return Paths.get(first).resolve(CONFIG_FILE_NAME);
  }

  /**
   * Rewrites {@code song-player.player-type} to {@code winamp} and
   * {@code song-player.winamp-exe-path} to {@code winampExePath} in the given config file. Failure
   * is logged rather than thrown, since the caller has already switched to Winamp in memory.
   */
  public static void persistWinampFallback(Path configFile, String winampExePath) {

    if (configFile == null) {
      LOG.warn("No external config file is known; the Winamp fallback (winamp-exe-path {}) "
          + "will not be persisted and VLC will be retried on the next startup", winampExePath);
      return;
    }
    try {
      String yaml = Files.exists(configFile)
          ? Files.readString(configFile, StandardCharsets.UTF_8)
          : "";
      Files.writeString(configFile, setSongPlayerWinamp(yaml, winampExePath),
          StandardCharsets.UTF_8);
      LOG.info("Updated {} with song-player.player-type: winamp and winamp-exe-path: {}",
          configFile, winampExePath);
    } catch (IOException e) {
      LOG.warn("Unable to persist the Winamp fallback to {}; VLC will be retried on the next "
          + "startup", configFile, e);
    }
  }

  /**
   * Returns {@code yaml} with {@code song-player.player-type} set to {@code winamp} and
   * {@code song-player.winamp-exe-path} set to {@code winampExePath}, inserting either key (or the
   * whole {@code song-player} block) if it is missing. A trailing comment on the
   * {@code player-type} line is preserved.
   */
  static String setSongPlayerWinamp(String yaml, String winampExePath) {

    String lineSeparator = yaml.contains("\r\n") ? "\r\n" : "\n";
    String exePathValue = ExternalConfigInitializer.toYamlScalar(winampExePath);

    List<String> lines = new ArrayList<>(Arrays.asList(yaml.split("\\R", -1)));

    int blockStart = -1;
    for (int i = 0; i < lines.size(); i++) {
      if (SONG_PLAYER_LINE.matcher(lines.get(i)).matches()) {
        blockStart = i;
        break;
      }
    }

    if (blockStart < 0) {
      StringBuilder sb = new StringBuilder(yaml);
      if (!yaml.isEmpty() && !yaml.endsWith("\n")) {
        sb.append(lineSeparator);
      }
      sb.append(SONG_PLAYER_KEY).append(':').append(lineSeparator);
      sb.append(DEFAULT_CHILD_INDENT).append(PLAYER_TYPE_KEY).append(": ")
          .append(WINAMP_PLAYER_TYPE).append(lineSeparator);
      sb.append(DEFAULT_CHILD_INDENT).append(WINAMP_EXE_PATH_KEY).append(": ")
          .append(exePathValue).append(lineSeparator);
      return sb.toString();
    }

    // The block runs until the next top-level (non-indented, non-blank, non-comment) line.
    int blockEnd = lines.size();
    for (int i = blockStart + 1; i < lines.size(); i++) {
      String line = lines.get(i);
      if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))
          && !line.startsWith("#")) {
        blockEnd = i;
        break;
      }
    }

    String childIndent = null;
    int playerTypeIndex = -1;
    boolean exePathSet = false;
    for (int i = blockStart + 1; i < blockEnd; i++) {
      String line = lines.get(i);

      Matcher childKey = CHILD_KEY_LINE.matcher(line);
      if (childIndent == null && childKey.matches()) {
        childIndent = childKey.group(1);
      }

      Matcher playerType = PLAYER_TYPE_LINE.matcher(line);
      if (playerTypeIndex < 0 && playerType.matches()) {
        String comment = playerType.group(2) != null ? playerType.group(2) : "";
        lines.set(i, playerType.group(1) + PLAYER_TYPE_KEY + ": " + WINAMP_PLAYER_TYPE + comment);
        playerTypeIndex = i;
        continue;
      }

      Matcher exePath = WINAMP_EXE_PATH_LINE.matcher(line);
      if (!exePathSet && exePath.matches()) {
        lines.set(i, exePath.group(1) + WINAMP_EXE_PATH_KEY + ": " + exePathValue);
        exePathSet = true;
      }
    }

    String indent = childIndent != null ? childIndent : DEFAULT_CHILD_INDENT;
    if (playerTypeIndex < 0) {
      playerTypeIndex = blockStart + 1;
      lines.add(playerTypeIndex, indent + PLAYER_TYPE_KEY + ": " + WINAMP_PLAYER_TYPE);
    }
    if (!exePathSet) {
      // Keep winamp-exe-path next to player-type, mirroring the bundled application.yml.
      lines.add(playerTypeIndex + 1, indent + WINAMP_EXE_PATH_KEY + ": " + exePathValue);
    }

    return String.join(lineSeparator, lines);
  }
}
