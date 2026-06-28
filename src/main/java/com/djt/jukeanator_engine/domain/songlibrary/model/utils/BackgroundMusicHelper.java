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
   */
  private static final String BACKGROUND_MUSIC = "BackgroundMusic.TXT";
  private static final String BACKGROUND_MUSIC_PLAYED = "BackgroundMusic_Played.TXT";
  private static final String BACKGROUND_MUSIC_NOT_PLAYED = "BackgroundMusic_NotPlayed.TXT";

  private List<String> playedSongs = new ArrayList<>();
  private List<String> notPlayedSongs = new ArrayList<>();

  public void initializeBackgroundMusic(String rootPath) throws IOException {

    try {

      this.notPlayedSongs =
          readNonBlankLines(rootPath + File.separator + BACKGROUND_MUSIC_NOT_PLAYED);
      this.playedSongs = readNonBlankLines(rootPath + File.separator + BACKGROUND_MUSIC_PLAYED);

    } catch (IOException ioe) {
      reset(rootPath);
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
  }

  public String getRandomSongFromBackgroundMusicPlaylist(String rootPath, String rootPathWindows,
      String rootPathUnix) throws IOException {

    if (this.notPlayedSongs.isEmpty()) {
      reset(rootPath);
    }

    String songPathName = null;
    if (this.notPlayedSongs.size() == 1) {
      songPathName = this.notPlayedSongs.get(0);
    } else {
      int index = ThreadLocalRandom.current().nextInt(this.notPlayedSongs.size());
      songPathName = this.notPlayedSongs.get(index);
    }
    
    this.notPlayedSongs.remove(songPathName);
    this.playedSongs.add(songPathName);

    // Normalize rootPathWindows in case the config value was loaded with a double backslash
    // after the drive letter (e.g. "R:\\Rock_On_Third" instead of "R:\Rock_On_Third").
    String normalizedRootPathWindows = rootPathWindows.replace(":\\\\", ":\\");
    OSType osType = OperatingSystemDetector.getOperatingSystem();

    // See if we need to fix up song pathnames that we read in.
    boolean switchToUnixFormat = false;
    boolean switchToWindowsFormat = false;
    if (osType == OSType.WINDOWS && !songPathName.contains(normalizedRootPathWindows)) {
      switchToWindowsFormat = true;
    } else if ((osType == OSType.LINUX || osType == OSType.MACOS)
        && !songPathName.contains(rootPathUnix)) {
      switchToUnixFormat = true;
    }
    if (switchToUnixFormat) {
      songPathName =
          songPathName.replace(normalizedRootPathWindows, rootPathUnix).replace("\\", "/");
    } else if (switchToWindowsFormat) {
      songPathName =
          songPathName.replace(rootPathUnix, normalizedRootPathWindows).replace("/", "\\");
    }

    return songPathName;
  }

  public void update(String rootPath) throws IOException {

    String playedSongsPathName = rootPath + File.separator + BACKGROUND_MUSIC_PLAYED;
    writeLines(playedSongsPathName, playedSongs);
    
    String notPlayedSongsPathName = rootPath + File.separator + BACKGROUND_MUSIC_NOT_PLAYED;
    writeLines(notPlayedSongsPathName, notPlayedSongs);
  }

  public void createBackgroundMusicFromTopSongs(String rootPath, List<String> topSongPathNames)
      throws IOException {

    String backgroundMusicPathName = rootPath + File.separator + BACKGROUND_MUSIC;

    writeLines(backgroundMusicPathName, topSongPathNames);
  }
}
