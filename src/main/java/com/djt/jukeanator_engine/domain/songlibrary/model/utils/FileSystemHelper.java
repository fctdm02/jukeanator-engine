package com.djt.jukeanator_engine.domain.songlibrary.model.utils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class FileSystemHelper {

  public boolean exists(String pathname) {
    return Files.exists(Path.of(pathname));
  }

  public void copyFile(String sourcePathname, String targetPathname) throws IOException {

    Files.copy(Path.of(sourcePathname), Path.of(targetPathname),
        StandardCopyOption.REPLACE_EXISTING);
  }

  public List<String> readNonBlankLines(String pathname) throws IOException {

    List<String> list = Files.readAllLines(Path.of(pathname), StandardCharsets.UTF_8).stream().map(String::trim)
        .filter(line -> !line.isEmpty()).toList();
    
    List<String> lines = new ArrayList<>();
    lines.addAll(list);
    return lines;
  }

  public void writeLines(String pathname, List<String> lines) throws IOException {

    try (BufferedWriter writer =
        Files.newBufferedWriter(Path.of(pathname), StandardCharsets.UTF_8)) {
      for (String line : lines) {
        writer.write(line);
        writer.newLine();
      }
    }
  }
}
