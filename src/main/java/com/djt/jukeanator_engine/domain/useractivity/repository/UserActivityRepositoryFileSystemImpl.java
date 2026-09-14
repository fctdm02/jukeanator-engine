package com.djt.jukeanator_engine.domain.useractivity.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import com.djt.jukeanator_engine.domain.common.repository.AbstractRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;

/**
 * JSON-Lines, filesystem-backed implementation of {@link UserActivityRepository}. Activity events
 * are high-frequency (every tab switch, every pagination click) -- unlike other filesystem
 * repositories in this codebase (e.g. {@code FinancialLedgerRepositoryFileSystemImpl}), which
 * rewrite one whole JSON object file per store, that pattern would mean re-reading and rewriting an
 * ever-growing file on every single event. Instead each record is appended as one JSON line (via
 * {@link #appendJsonLine}) to a file scoped by location and day:
 * {@code <basePath>/activity/location-<id>/<yyyy-MM-dd>.jsonl} -- bounding any one file's size to a
 * single location's single day of activity and keeping the write itself {@code O(1)}.
 */
public final class UserActivityRepositoryFileSystemImpl extends AbstractRepositoryFileSystemImpl
    implements UserActivityRepository {

  private static final DateTimeFormatter DAY_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  private String activityDir;

  public UserActivityRepositoryFileSystemImpl(String basePath) {
    super(basePath);
    requireNonNull(basePath, "basePath cannot be null");
    this.activityDir = basePath + File.separator + "activity";
  }

  @Override
  public void setBasePath(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    super.setBasePath(basePath);
    this.activityDir = basePath + File.separator + "activity";
  }

  @Override
  public void record(UserActivityRecord record) {

    requireNonNull(record, "record cannot be null");

    String locationSegment = "location-" + record.locationId();
    String dayFileName = DAY_FORMATTER.format(record.occurredAt()) + ".jsonl";
    String filePath =
        activityDir + File.separator + locationSegment + File.separator + dayFileName;

    appendJsonLine(filePath, record);
  }

  @Override
  public List<UserActivityRecord> findRecentActivity(Integer locationId, int limit) {

    requireNonNull(locationId, "locationId cannot be null");

    Path locationDir = Path.of(activityDir, "location-" + locationId);
    if (!Files.isDirectory(locationDir)) {
      return List.of();
    }

    // One .jsonl file per day -- newest day first, so we can stop reading as soon as `limit` is
    // reached without ever touching older files.
    List<Path> dayFilesNewestFirst;
    try (Stream<Path> listing = Files.list(locationDir)) {
      dayFilesNewestFirst = listing.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
          .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
          .toList();
    } catch (IOException ioe) {
      throw new UncheckedIOException("Could not list activity directory: " + locationDir, ioe);
    }

    List<UserActivityRecord> result = new ArrayList<>();
    for (Path dayFile : dayFilesNewestFirst) {
      if (result.size() >= limit) {
        break;
      }

      List<String> lines;
      try {
        lines = Files.readAllLines(dayFile);
      } catch (IOException ioe) {
        throw new UncheckedIOException("Could not read activity file: " + dayFile, ioe);
      }

      // Each file is append-only in chronological order, so the last line is that day's newest
      // event -- walk backwards to keep the overall newest-first ordering across files.
      for (int i = lines.size() - 1; i >= 0 && result.size() < limit; i--) {
        String line = lines.get(i);
        if (line.isBlank()) {
          continue;
        }
        try {
          result.add(MAPPER.readValue(line, UserActivityRecord.class));
        } catch (IOException ioe) {
          // Skip a single malformed line rather than fail the whole read.
        }
      }
    }
    return result;
  }

  @Override
  public void purgeOlderThan(Instant cutoff) {

    requireNonNull(cutoff, "cutoff cannot be null");

    Path root = Path.of(activityDir);
    if (!Files.isDirectory(root)) {
      return;
    }

    // Each day-file's name IS its UTC date (yyyy-MM-dd), which sorts/compares lexicographically
    // identically to chronological order -- so files to delete can be picked by a plain string
    // comparison against the formatted cutoff, without opening or parsing any file content.
    String cutoffDay = DAY_FORMATTER.format(cutoff);

    List<Path> locationDirs;
    try (Stream<Path> listing = Files.list(root)) {
      locationDirs = listing.filter(Files::isDirectory).toList();
    } catch (IOException ioe) {
      throw new UncheckedIOException("Could not list activity root directory: " + root, ioe);
    }

    for (Path locationDir : locationDirs) {
      purgeExpiredDayFiles(locationDir, cutoffDay);
    }
  }

  private void purgeExpiredDayFiles(Path locationDir, String cutoffDay) {

    List<Path> dayFiles;
    try (Stream<Path> listing = Files.list(locationDir)) {
      dayFiles = listing.filter(p -> p.getFileName().toString().endsWith(".jsonl")).toList();
    } catch (IOException ioe) {
      throw new UncheckedIOException("Could not list activity directory: " + locationDir, ioe);
    }

    for (Path dayFile : dayFiles) {
      String fileName = dayFile.getFileName().toString();
      String day = fileName.substring(0, fileName.length() - ".jsonl".length());
      if (day.compareTo(cutoffDay) < 0) {
        try {
          Files.delete(dayFile);
        } catch (IOException ioe) {
          throw new UncheckedIOException("Could not delete expired activity file: " + dayFile, ioe);
        }
      }
    }
  }
}
