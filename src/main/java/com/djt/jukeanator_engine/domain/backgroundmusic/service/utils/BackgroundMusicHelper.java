package com.djt.jukeanator_engine.domain.backgroundmusic.service.utils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import com.djt.jukeanator_engine.domain.common.utils.FileSystemHelper;

/**
 * Reads/writes the canonical {@code BackgroundMusic.TXT} playlist file. All played/not-played
 * rotation state now lives in {@code BackgroundMusicSongEntity}/{@code SmartBackgroundMusicSongEntity}
 * records persisted via {@code BackgroundMusicRepository}/{@code SmartBackgroundMusicRepository} —
 * this helper no longer tracks any of that state itself.
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
   * Collapses a double backslash after a drive letter (e.g. "R:\\Rock_On_Third" instead of
   * "R:\Rock_On_Third") that can occur when a Windows path is round-tripped through config/YAML.
   */
  public String normalizeDriveLetterBackslashes(String songPathName) {
    return songPathName.replace(":\\\\", ":\\");
  }
}
