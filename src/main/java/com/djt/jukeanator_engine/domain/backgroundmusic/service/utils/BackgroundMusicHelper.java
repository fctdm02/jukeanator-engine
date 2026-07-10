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
 * {@code BackgroundMusicRepository}/{@code SmartBackgroundMusicRepository} — this helper no longer
 * tracks any of that state itself.
 */
public class BackgroundMusicHelper extends FileSystemHelper {

  private static final String BACKGROUND_MUSIC = "BackgroundMusic.TXT";

  public static final String SMART_BACKGROUND_MUSIC_GENRE_EXCLUSIONS_FILENAME =
      "SmartBackgroundMusicGenreExclusions.TXT";

  public static final String SMART_BACKGROUND_MUSIC_ALBUM_INCLUSIONS_FILENAME =
      "SmartBackgroundMusicAlbumInclusions.TXT";

  /**
   * Reads the canonical background-music playlist file. If it does not exist, this method does not
   * create it — callers needing the top-songs fallback should catch the resulting
   * {@link IOException} and call {@link #createBackgroundMusicFromTopSongs}.
   */
  public List<String> readBackgroundMusicPlaylist(String rootPath) throws IOException {
    return readNonBlankLines(rootPath + File.separator + BACKGROUND_MUSIC);
  }

  /**
   * Reads {@value #SMART_BACKGROUND_MUSIC_GENRE_EXCLUSIONS_FILENAME} — one genre name per line —
   * from the same root path as {@code BackgroundMusic.TXT}. Any genre listed in this file is
   * excluded from smart-addition candidate selection. The file is optional: if it does not exist,
   * an empty list is returned (no genres excluded).
   */
  public List<String> readSmartBackgroundMusicGenreExclusions(String rootPath) throws IOException {

    String path = rootPath + File.separator + SMART_BACKGROUND_MUSIC_GENRE_EXCLUSIONS_FILENAME;
    if (!exists(path)) {
      return List.of();
    }
    return readNonBlankLines(path);
  }

  /**
   * Reads {@value #SMART_BACKGROUND_MUSIC_ALBUM_INCLUSIONS_FILENAME} — one album path per line —
   * from the same root path as {@code BackgroundMusic.TXT}. Every album listed here is a
   * "favorite album": whenever the smart-additions pool is (re)built, songs from these albums are
   * interleaved into it with no source song of their own (see
   * {@code SmartAdditionReason.SONG_FROM_FAVORITE_ALBUM}). Each line is matched against the
   * library by path <em>suffix</em> (see {@code BackgroundMusicServiceImpl#isFavoriteAlbum}), so
   * it need not be the album's full absolute filesystem path — anything from a bare
   * {@code Genre/Artist/Album} up to the full path works. The file is optional: if it does not
   * exist, an empty list is returned (no favorite albums).
   */
  public List<String> readSmartBackgroundMusicAlbumInclusions(String rootPath) throws IOException {

    String path = rootPath + File.separator + SMART_BACKGROUND_MUSIC_ALBUM_INCLUSIONS_FILENAME;
    if (!exists(path)) {
      return List.of();
    }
    return readNonBlankLines(path);
  }

  /**
   * Writes a fresh {@code BackgroundMusic.TXT} populated from the given top-played song paths. Used
   * as a fallback when no playlist file exists yet.
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
