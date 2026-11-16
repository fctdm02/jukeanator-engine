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
   * Reads a playlist file and returns its lines.
   *
   * <p>
   * Blank lines and comment lines (starting with {@code #}) are skipped. A line is usually a song
   * file path, but may be free text such as an artist and title copied from a chart; resolving each
   * line to a song is left to the caller (see {@link PlaylistSongResolver}).
   *
   * @param file Source file.
   * @return Ordered list of trimmed lines (never {@code null}).
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
}
