package com.djt.jukeanator_engine.domain.songqueue.service.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for saving and loading playlists as plain-text files.
 *
 * <p>
 * File format: one song file path per line. Blank lines and lines starting with {@code #} are
 * treated as comments and ignored on load.
 */
public final class PlaylistManager {

  private PlaylistManager() {}

  // ─────────────────────────────────────────────────────────────────────────
  // SAVE
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Writes each path in {@code songPaths} as a single line to {@code file}.
   *
   * @param file Destination file. Created (including parent dirs) if absent.
   * @param songPaths Ordered list of song file paths to persist.
   * @throws IOException if the file cannot be written.
   */
  public static void savePlayList(File file, List<String> songPaths) throws IOException {

    File parent = file.getParentFile();
    if (parent != null && !parent.exists()) {
      parent.mkdirs();
    }

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("# JukeANator PlayList — " + new java.util.Date());
      writer.newLine();
      for (String path : songPaths) {
        if (path != null && !path.isBlank()) {
          writer.write(path);
          writer.newLine();
        }
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // LOAD
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Reads a playlist file and returns the contained song file paths.
   *
   * <p>
   * Blank lines and comment lines (starting with {@code #}) are skipped. Non-existent paths are
   * included so the caller can decide how to handle missing files.
   *
   * @param file Source file.
   * @return Ordered list of song file paths (never {@code null}).
   * @throws IOException if the file cannot be read.
   */
  public static List<String> loadPlayList(File file) throws IOException {

    List<String> paths = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
          paths.add(trimmed);
        }
      }
    }

    return paths;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // DEFAULT FILE HELPER
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Returns a suggested default playlist file using the current timestamp, placed under
   * {@code ~/JukeANator/playlists/}.
   */
  public static File defaultPlayListFile() {

    String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
    return new File(System.getProperty("user.home"),
        "JukeANator" + File.separator + "playlists" + File.separator + "playlist_" + ts + ".txt");
  }
}
