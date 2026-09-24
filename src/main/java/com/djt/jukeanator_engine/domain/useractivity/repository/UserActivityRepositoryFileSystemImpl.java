package com.djt.jukeanator_engine.domain.useractivity.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import com.djt.jukeanator_engine.domain.common.repository.AbstractRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.useractivity.model.PendingUserActivity;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.fasterxml.jackson.core.type.TypeReference;

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

  // Slave-side outbox position -- see findPendingMasterSync. Lives directly in the activity
  // directory, beside (never inside) the location-<id> directories every read/purge walks.
  static final String SYNC_CURSOR_FILENAME = "master-sync-cursor.json";

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

  // synchronized (like changeLocationId below) so an @Async append from UserActivityEventListener
  // can never land in a location directory while that directory is being moved.
  @Override
  public synchronized void record(UserActivityRecord record) {

    requireNonNull(record, "record cannot be null");

    appendJsonLine(dayFilePath(record).toString(), record);
  }

  private Path dayFilePath(UserActivityRecord record) {
    return Path.of(activityDir, "location-" + record.locationId(),
        DAY_FORMATTER.format(record.occurredAt()) + ".jsonl");
  }

  /**
   * Day-files are append-only, so this slave's outbox is simply "every byte past what master has
   * acknowledged" in each one: {@link #SYNC_CURSOR_FILENAME} maps each day-file (relative to the
   * activity directory) to the byte offset up to which master has acknowledged its lines. A file
   * with no entry is entirely unacknowledged -- including every file written before this existed,
   * which is simply pushed once (master's copy is idempotent on {@code activityId}).
   */
  @Override
  public synchronized List<PendingUserActivity> findPendingMasterSync(int limit) {

    Path root = Path.of(activityDir);
    List<PendingUserActivity> result = new ArrayList<>();
    if (!Files.isDirectory(root) || limit <= 0) {
      return result;
    }

    Map<String, Long> cursor = readSyncCursor();

    for (Path dayFile : listDayFilesOldestFirst(root)) {
      String relativePath = relativePath(root, dayFile);
      long acknowledgedOffset = cursor.getOrDefault(relativePath, Long.valueOf(0)).longValue();

      byte[] unacknowledged;
      try {
        if (Files.size(dayFile) <= acknowledgedOffset) {
          continue;
        }
        byte[] content = Files.readAllBytes(dayFile);
        unacknowledged = Arrays.copyOfRange(content, (int) acknowledgedOffset, content.length);
      } catch (IOException ioe) {
        throw new UncheckedIOException("Could not read activity file: " + dayFile, ioe);
      }

      int lineStart = 0;
      for (int i = 0; i < unacknowledged.length && result.size() < limit; i++) {
        if (unacknowledged[i] != '\n') {
          continue;
        }
        long lineStartOffset = acknowledgedOffset + lineStart;
        long lineEndOffset = acknowledgedOffset + i + 1;
        String line = new String(unacknowledged, lineStart, i - lineStart, StandardCharsets.UTF_8)
            .strip();
        lineStart = i + 1;

        UserActivityRecord record = parseLine(line);
        // A malformed line or a master-relayed (SYSTEM) one is never pushed; the cursor still moves
        // past it once a later line in the same file is acknowledged.
        if (record == null || SystemPrincipal.SYSTEM_USERNAME.equals(record.username())) {
          continue;
        }

        // A line written before activity ids existed gets a stand-in derived from its own
        // position -- stable across sweeps, so a re-push of it is still idempotent on master.
        String activityId = record.activityId() != null ? record.activityId()
            : UUID.nameUUIDFromBytes((relativePath + "#" + lineStartOffset)
                .getBytes(StandardCharsets.UTF_8)).toString();
        result.add(new PendingUserActivity(relativePath + "#" + lineEndOffset,
            record.with(record.locationId(), activityId)));
      }

      if (result.size() >= limit) {
        break;
      }
    }
    return result;
  }

  @Override
  public synchronized void markSyncedToMaster(List<PendingUserActivity> acknowledged) {

    if (acknowledged.isEmpty()) {
      return;
    }

    Path root = Path.of(activityDir);
    Map<String, Long> cursor = readSyncCursor();

    for (PendingUserActivity pending : acknowledged) {
      int separator = pending.cursor().lastIndexOf('#');
      String relativePath = pending.cursor().substring(0, separator);
      Long endOffset = Long.valueOf(pending.cursor().substring(separator + 1));
      cursor.merge(relativePath, endOffset, (existing, next) -> Math.max(existing, next));
    }

    // Day-files removed since (retention purge, location re-key) no longer need a cursor entry.
    cursor.keySet().removeIf(relativePath -> !Files.exists(root.resolve(relativePath)));

    writeSyncCursor(cursor);
  }

  @Override
  public synchronized boolean recordIfAbsent(UserActivityRecord record) {

    requireNonNull(record, "record cannot be null");
    requireNonNull(record.activityId(), "record.activityId() cannot be null");

    // A given activity's day-file is fully determined by its location and occurredAt, so a
    // duplicate can only ever be in that one file.
    Path dayFile = dayFilePath(record);
    if (Files.exists(dayFile)) {
      try {
        for (String line : Files.readAllLines(dayFile, StandardCharsets.UTF_8)) {
          UserActivityRecord existing = parseLine(line.strip());
          if (existing != null && record.activityId().equals(existing.activityId())) {
            return false;
          }
        }
      } catch (IOException ioe) {
        throw new UncheckedIOException("Could not read activity file: " + dayFile, ioe);
      }
    }

    appendJsonLine(dayFile.toString(), record);
    return true;
  }

  private static UserActivityRecord parseLine(String line) {
    if (line.isBlank()) {
      return null;
    }
    try {
      return MAPPER.readValue(line, UserActivityRecord.class);
    } catch (IOException ioe) {
      return null;
    }
  }

  private static List<Path> listDayFilesOldestFirst(Path root) {

    try (Stream<Path> listing = Files.walk(root, 2)) {
      return listing
          .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
          .sorted(Comparator.comparing((Path p) -> p.getFileName().toString())
              .thenComparing(Path::toString))
          .toList();
    } catch (IOException ioe) {
      throw new UncheckedIOException("Could not list activity directory: " + root, ioe);
    }
  }

  private static String relativePath(Path root, Path dayFile) {
    return root.relativize(dayFile).toString().replace(File.separatorChar, '/');
  }

  private Map<String, Long> readSyncCursor() {

    Path cursorFile = Path.of(activityDir, SYNC_CURSOR_FILENAME);
    if (!Files.exists(cursorFile)) {
      return new HashMap<>();
    }
    try {
      return new HashMap<>(
          MAPPER.readValue(cursorFile.toFile(), new TypeReference<Map<String, Long>>() {}));
    } catch (IOException ioe) {
      throw new UncheckedIOException("Could not read activity sync cursor: " + cursorFile, ioe);
    }
  }

  private void writeSyncCursor(Map<String, Long> cursor) {

    Path cursorFile = Path.of(activityDir, SYNC_CURSOR_FILENAME);
    try {
      Files.createDirectories(cursorFile.getParent());
      Files.writeString(cursorFile, OBJECT_WRITER.writeValueAsString(cursor),
          StandardCharsets.UTF_8);
    } catch (IOException ioe) {
      throw new UncheckedIOException("Could not write activity sync cursor: " + cursorFile, ioe);
    }
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

  /**
   * Moves every day-file from {@code location-<oldLocationId>} into {@code
   * location-<newLocationId>}, rewriting each line's {@code locationId} along the way (the
   * filesystem counterpart of the JPA {@code user_activity.location_id} update), then removes the
   * old directory. A day-file that already exists under the new id (e.g. one appended to between
   * the id change and this event) is appended to rather than overwritten, so nothing is lost --
   * at worst that one day's lines end up out of chronological order. A malformed line is carried
   * over verbatim, matching findRecentActivity's skip-don't-fail handling of one.
   */
  @Override
  public synchronized void changeLocationId(Integer oldLocationId, Integer newLocationId) {

    requireNonNull(oldLocationId, "oldLocationId cannot be null");
    requireNonNull(newLocationId, "newLocationId cannot be null");

    if (oldLocationId.equals(newLocationId)) {
      return;
    }

    Path oldDir = Path.of(activityDir, "location-" + oldLocationId);
    if (!Files.isDirectory(oldDir)) {
      return;
    }
    Path newDir = Path.of(activityDir, "location-" + newLocationId);

    List<Path> dayFiles;
    try (Stream<Path> listing = Files.list(oldDir)) {
      dayFiles = listing.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
          .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()))
          .toList();
    } catch (IOException ioe) {
      throw new UncheckedIOException("Could not list activity directory: " + oldDir, ioe);
    }

    try {
      Files.createDirectories(newDir);

      for (Path dayFile : dayFiles) {
        List<String> rewritten = new ArrayList<>();
        for (String line : Files.readAllLines(dayFile, StandardCharsets.UTF_8)) {
          if (!line.isBlank()) {
            rewritten.add(withLocationId(line, newLocationId));
          }
        }
        Files.write(newDir.resolve(dayFile.getFileName()), rewritten, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        Files.delete(dayFile);
      }
    } catch (IOException ioe) {
      throw new UncheckedIOException(
          "Could not move activity directory: " + oldDir + " to: " + newDir, ioe);
    }

    try {
      Files.deleteIfExists(oldDir);
    } catch (IOException ioe) {
      // Something other than a day-file was left behind -- harmless, since only .jsonl files are
      // ever read from a location directory.
    }
  }

  private static String withLocationId(String line, Integer newLocationId) {

    try {
      UserActivityRecord record = MAPPER.readValue(line, UserActivityRecord.class);
      return OBJECT_WRITER.writeValueAsString(record.with(newLocationId, record.activityId()));
    } catch (IOException ioe) {
      return line;
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
