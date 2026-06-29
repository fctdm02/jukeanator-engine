package com.djt.jukeanator_engine.domain.songlibrary.model.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector.OSType;

public class BackgroundMusicHelper extends FileSystemHelper {

  /*
   * - BACKGROUND_MUSIC is the canonical master playlist
   *
   * - Initially, BACKGROUND_MUSIC_PLAYED will be empty and BACKGROUND_MUSIC_NOT_PLAYED will be
   * copied from BACKGROUND_MUSIC
   *
   * - As background songs are played, via a call to getRandomSongFromBackgroundMusicPlaylist(),
   * they are removed from BACKGROUND_MUSIC_NOT_PLAYED and added to BACKGROUND_MUSIC_PLAYED
   *
   * - When BACKGROUND_MUSIC_NOT_PLAYED is empty, then the process will start over,
   * BACKGROUND_MUSIC_PLAYED will be emptied and BACKGROUND_MUSIC will be copied to
   * BACKGROUND_MUSIC_NOT_PLAYED
   *
   * Smart additions mirror the same played/not-played pattern using a separate pair of files.
   * SMART_ADDITIONS_NOT_PLAYED is populated externally (by SongQueueServiceImpl) with song paths
   * derived from genre/artist/album lookups. As songs are drawn via
   * getRandomSmartAdditionSongPath() they migrate to SMART_ADDITIONS_PLAYED. When
   * SMART_ADDITIONS_NOT_PLAYED is exhausted the two lists reset so the cycle restarts.
   */
  private static final String BACKGROUND_MUSIC = "BackgroundMusic.TXT";
  private static final String BACKGROUND_MUSIC_PLAYED = "BackgroundMusic_Played.TXT";
  private static final String BACKGROUND_MUSIC_NOT_PLAYED = "BackgroundMusic_NotPlayed.TXT";

  // Smart-additions played/not-played files (separate from the core background music files)
  private static final String SMART_ADDITIONS_PLAYED = "BackgroundMusic_SmartAdditions_Played.TXT";
  private static final String SMART_ADDITIONS_NOT_PLAYED =
      "BackgroundMusic_SmartAdditions_NotPlayed.TXT";

  private List<String> playedSongs = new ArrayList<>();
  private List<String> notPlayedSongs = new ArrayList<>();

  // Smart-additions state (in-memory, persisted via updateSmartAdditions())
  private List<String> smartAdditionsPlayed = new ArrayList<>();
  private List<String> smartAdditionsNotPlayed = new ArrayList<>();

  // ── Core background-music lifecycle ─────────────────────────────────────

  public void initializeBackgroundMusic(String rootPath) throws IOException {

    try {
      this.notPlayedSongs =
          readNonBlankLines(rootPath + File.separator + BACKGROUND_MUSIC_NOT_PLAYED);
      this.playedSongs = readNonBlankLines(rootPath + File.separator + BACKGROUND_MUSIC_PLAYED);
    } catch (IOException ioe) {
      reset(rootPath);
    }

    // Initialize smart-additions state — best-effort; missing files are treated as empty lists.
    try {
      this.smartAdditionsNotPlayed =
          readNonBlankLines(rootPath + File.separator + SMART_ADDITIONS_NOT_PLAYED);
      this.smartAdditionsPlayed =
          readNonBlankLines(rootPath + File.separator + SMART_ADDITIONS_PLAYED);
    } catch (IOException ioe) {
      // Smart-additions files don't exist yet — start with empty lists; they will be
      // written the first time updateSmartAdditions() is called.
      this.smartAdditionsNotPlayed = new ArrayList<>();
      this.smartAdditionsPlayed = new ArrayList<>();
    }
  }

  private void reset(String rootPath) throws IOException {

    // We do this just to create the file, we will empty it out with the next line.
    copyFile(rootPath + File.separator + BACKGROUND_MUSIC,
        rootPath + File.separator + BACKGROUND_MUSIC_PLAYED);

    this.playedSongs = new ArrayList<>();
    writeLines(rootPath + File.separator + BACKGROUND_MUSIC_PLAYED, this.playedSongs);

    copyFile(rootPath + File.separator + BACKGROUND_MUSIC,
        rootPath + File.separator + BACKGROUND_MUSIC_NOT_PLAYED);

    this.notPlayedSongs =
        readNonBlankLines(rootPath + File.separator + BACKGROUND_MUSIC_NOT_PLAYED);

    // Reset smart-additions files too so they start fresh alongside the core playlist.
    resetSmartAdditions(rootPath);
  }

  public String getRandomSongFromBackgroundMusicPlaylist(String rootPath, String rootPathWindows,
      String rootPathUnix) throws IOException {

    if (this.notPlayedSongs.isEmpty()) {
      reset(rootPath);
    }

    String songPathName;
    if (this.notPlayedSongs.size() == 1) {
      songPathName = this.notPlayedSongs.get(0);
    } else {
      int index = ThreadLocalRandom.current().nextInt(this.notPlayedSongs.size());
      songPathName = this.notPlayedSongs.get(index);
    }

    this.notPlayedSongs.remove(songPathName);
    this.playedSongs.add(songPathName);

    songPathName = normalizePathForCurrentOS(songPathName, rootPathWindows, rootPathUnix);
    return songPathName;
  }

  public void update(String rootPath) throws IOException {

    writeLines(rootPath + File.separator + BACKGROUND_MUSIC_PLAYED, playedSongs);
    writeLines(rootPath + File.separator + BACKGROUND_MUSIC_NOT_PLAYED, notPlayedSongs);
  }

  public void createBackgroundMusicFromTopSongs(String rootPath, List<String> topSongPathNames)
      throws IOException {

    writeLines(rootPath + File.separator + BACKGROUND_MUSIC, topSongPathNames);
  }

  // ── Smart-additions lifecycle ────────────────────────────────────────────

  /**
   * Replaces the smart-additions not-played pool with the supplied list of song paths and resets
   * the played list to empty. Called by {@code SongQueueServiceImpl} whenever a new batch of
   * smart-addition candidates has been computed.
   *
   * @param rootPath the root data directory
   * @param candidatePathNames the full set of smart-addition candidate paths for this cycle
   */
  public void resetSmartAdditions(String rootPath) throws IOException {

    this.smartAdditionsPlayed = new ArrayList<>();
    this.smartAdditionsNotPlayed = new ArrayList<>();

    writeLines(rootPath + File.separator + SMART_ADDITIONS_PLAYED, this.smartAdditionsPlayed);
    writeLines(rootPath + File.separator + SMART_ADDITIONS_NOT_PLAYED,
        this.smartAdditionsNotPlayed);
  }

  /**
   * Loads the smart-additions not-played pool from the supplied candidate list, discarding any
   * entries that are already in the played list so that songs are never repeated within a cycle.
   *
   * @param rootPath the root data directory
   * @param candidatePathNames new candidates to load into the not-played pool
   */
  public void loadSmartAdditionCandidates(String rootPath, List<String> candidatePathNames)
      throws IOException {

    // Preserve any paths already played in this cycle so they are excluded from the new pool.
    List<String> newNotPlayed = new ArrayList<>(candidatePathNames);
    newNotPlayed.removeAll(this.smartAdditionsPlayed);

    this.smartAdditionsNotPlayed = newNotPlayed;
    writeLines(rootPath + File.separator + SMART_ADDITIONS_NOT_PLAYED,
        this.smartAdditionsNotPlayed);
  }

  /**
   * Picks and removes one random path from the smart-additions not-played pool, moves it to the
   * played pool, and returns the (OS-normalized) path.
   *
   * <p>
   * When the not-played pool is empty the played/not-played lists are reset and the method returns
   * {@code null} so the caller can fall back to the core playlist. The reset will be repopulated on
   * the next call to {@link #loadSmartAdditionCandidates}.
   *
   * @param rootPath the root data directory
   * @param rootPathWindows Windows-format root path (used for path normalisation)
   * @param rootPathUnix Unix-format root path (used for path normalisation)
   * @return a normalized song path, or {@code null} if the pool was exhausted (reset performed)
   */
  public String getRandomSmartAdditionSongPath(String rootPath, String rootPathWindows,
      String rootPathUnix) throws IOException {

    if (this.smartAdditionsNotPlayed.isEmpty()) {
      // Pool exhausted — reset so the next loadSmartAdditionCandidates() starts fresh.
      resetSmartAdditions(rootPath);
      return null;
    }

    String songPathName;
    if (this.smartAdditionsNotPlayed.size() == 1) {
      songPathName = this.smartAdditionsNotPlayed.get(0);
    } else {
      int index = ThreadLocalRandom.current().nextInt(this.smartAdditionsNotPlayed.size());
      songPathName = this.smartAdditionsNotPlayed.get(index);
    }

    this.smartAdditionsNotPlayed.remove(songPathName);
    this.smartAdditionsPlayed.add(songPathName);

    songPathName = normalizePathForCurrentOS(songPathName, rootPathWindows, rootPathUnix);
    return songPathName;
  }

  /**
   * Persists the current in-memory smart-additions state to disk. Call after every draw from
   * {@link #getRandomSmartAdditionSongPath} so that the played/not-played files stay in sync.
   */
  public void updateSmartAdditions(String rootPath) throws IOException {

    writeLines(rootPath + File.separator + SMART_ADDITIONS_PLAYED, smartAdditionsPlayed);
    writeLines(rootPath + File.separator + SMART_ADDITIONS_NOT_PLAYED, smartAdditionsNotPlayed);
  }

  /**
   * Returns the number of smart-addition songs remaining in the not-played pool.
   */
  public int getSmartAdditionsNotPlayedCount() {
    return smartAdditionsNotPlayed.size();
  }

  // ── Path normalisation (shared by both playlist kinds) ───────────────────

  /**
   * Translates a song path read from a text file into the format required by the OS currently
   * running the application.
   */
  private String normalizePathForCurrentOS(String songPathName, String rootPathWindows,
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

    return songPathName;
  }
}
