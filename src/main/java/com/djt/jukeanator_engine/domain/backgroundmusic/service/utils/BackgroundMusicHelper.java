package com.djt.jukeanator_engine.domain.backgroundmusic.service.utils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import com.djt.jukeanator_engine.domain.common.utils.FileSystemHelper;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector.OSType;

/**
 * Reads/writes the canonical {@code BackgroundMusic.TXT} playlist file and normalizes song paths
 * between OS-specific formats. All played/not-played rotation state now lives in
 * {@code BackgroundMusicSongEntity}/{@code SmartBackgroundMusicSongEntity} records persisted via
 * {@code BackgroundMusicRepository}/{@code SmartBackgroundMusicRepository} — this helper no
 * longer tracks any of that state itself.
 */
public class BackgroundMusicHelper extends FileSystemHelper {

  private static final String BACKGROUND_MUSIC = "BackgroundMusic.TXT";

  /**
   * Reads the canonical background-music playlist file. If it does not exist, this method does
   * not create it — callers needing the top-songs fallback should catch the resulting
   * {@link IOException} and call {@link #createBackgroundMusicFromTopSongs}.
   */
  public List<String> readBackgroundMusicPlaylist(String rootPath) throws IOException {
    return readNonBlankLines(rootPath + File.separator + BACKGROUND_MUSIC);
  }

  /**
   * Writes a fresh {@code BackgroundMusic.TXT} populated from the given top-played song paths.
   * Used as a fallback when no playlist file exists yet.
   */
  public void createBackgroundMusicFromTopSongs(String rootPath, List<String> topSongPathNames)
      throws IOException {

    writeLines(rootPath + File.separator + BACKGROUND_MUSIC, topSongPathNames);
  }

  /**
   * Translates a song path read from a file into the format required by the OS currently running
   * the application.
   */
  public String normalizePathForCurrentOS(String songPathName, String rootPathWindows,
      String rootPathUnix) {

    // Normalize rootPathWindows in case the config value was loaded with a double backslash
    // after the drive letter (e.g. "R:\\Rock_On_Third" instead of "R:\Rock_On_Third").
    String normalizedRootPathWindows = rootPathWindows.replace(":\\\\", ":\\");
    OSType osType = OperatingSystemDetector.getOperatingSystem();

    boolean switchToUnixFormat = false;
    boolean switchToWindowsFormat = false;

    if (osType == OSType.WINDOWS && !songPathName.contains(normalizedRootPathWindows)) {
      switchToWindowsFormat = true;
    } else if ((osType == OSType.LINUX || osType == OSType.MACOS)
        && !songPathName.contains(rootPathUnix)) {
      switchToUnixFormat = true;
    }

    if (switchToUnixFormat) {
      return songPathName.replace(normalizedRootPathWindows, rootPathUnix).replace("\\", "/");
    } else if (switchToWindowsFormat) {
      return songPathName.replace(rootPathUnix, normalizedRootPathWindows).replace("/", "\\");
    }

    return songPathName.replace(":\\\\", ":\\");
  }
}
