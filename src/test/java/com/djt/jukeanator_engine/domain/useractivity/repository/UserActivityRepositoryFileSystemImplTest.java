package com.djt.jukeanator_engine.domain.useractivity.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivitySource;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Unit tests for {@link UserActivityRepositoryFileSystemImpl}. */
class UserActivityRepositoryFileSystemImplTest {

  private static final ObjectMapper MAPPER = ObjectMappers.create();
  private static final DateTimeFormatter DAY_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  @Test
  void record_appendsOneJsonLine_toLocationAndDayScopedFile(@TempDir Path basePath)
      throws Exception {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    Instant occurredAt = Instant.now();
    UserActivityRecord record = new UserActivityRecord(Integer.valueOf(7),
        UserActivitySource.SWING_UI, "LOCAL", UserActivityType.TAB_NAVIGATION, occurredAt,
        Map.of("tabName", "GENRES"));

    repository.record(record);

    Path expectedFile = basePath.resolve("activity").resolve("location-7")
        .resolve(DAY_FORMATTER.format(occurredAt) + ".jsonl");
    assertTrue(Files.exists(expectedFile), "Expected activity file to exist: " + expectedFile);

    List<String> lines = Files.readAllLines(expectedFile);
    assertEquals(1, lines.size());

    UserActivityRecord reloaded = MAPPER.readValue(lines.get(0), UserActivityRecord.class);
    assertEquals(Integer.valueOf(7), reloaded.locationId());
    assertEquals(UserActivitySource.SWING_UI, reloaded.source());
    assertEquals("LOCAL", reloaded.username());
    assertEquals(UserActivityType.TAB_NAVIGATION, reloaded.activityType());
    assertEquals("GENRES", reloaded.details().get("tabName"));
  }

  @Test
  void record_appendsWithoutRewriting_soMultipleEventsAccumulateInOneFile(@TempDir Path basePath)
      throws Exception {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    Instant occurredAt = Instant.now();
    for (int i = 0; i < 3; i++) {
      repository.record(new UserActivityRecord(Integer.valueOf(1), UserActivitySource.SWING_UI,
          "LOCAL", UserActivityType.PAGE_NAVIGATION, occurredAt, Map.of("direction", "next")));
    }

    Path expectedFile = basePath.resolve("activity").resolve("location-1")
        .resolve(DAY_FORMATTER.format(occurredAt) + ".jsonl");

    List<String> lines = Files.readAllLines(expectedFile);
    assertEquals(3, lines.size());
  }

  @Test
  void record_scopesDifferentLocations_toSeparateFiles(@TempDir Path basePath) throws Exception {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    Instant occurredAt = Instant.now();
    repository.record(new UserActivityRecord(Integer.valueOf(1), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, occurredAt, Map.of()));
    repository.record(new UserActivityRecord(Integer.valueOf(2), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, occurredAt, Map.of()));

    String dayFileName = DAY_FORMATTER.format(occurredAt) + ".jsonl";
    assertTrue(
        Files.exists(basePath.resolve("activity").resolve("location-1").resolve(dayFileName)));
    assertTrue(
        Files.exists(basePath.resolve("activity").resolve("location-2").resolve(dayFileName)));
  }

  @Test
  void findRecentActivity_returnsNewestFirst_withinASingleDayFile(@TempDir Path basePath) {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    Instant base = Instant.now();
    repository.record(recordAt(base, "first"));
    repository.record(recordAt(base.plusSeconds(1), "second"));
    repository.record(recordAt(base.plusSeconds(2), "third"));

    List<UserActivityRecord> recent = repository.findRecentActivity(Integer.valueOf(1), 10);

    assertEquals(3, recent.size());
    assertEquals("third", recent.get(0).details().get("label"));
    assertEquals("second", recent.get(1).details().get("label"));
    assertEquals("first", recent.get(2).details().get("label"));
  }

  @Test
  void findRecentActivity_spansMultipleDayFiles_stillNewestFirst(@TempDir Path basePath) {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    Instant today = Instant.now();
    Instant yesterday = today.minus(1, java.time.temporal.ChronoUnit.DAYS);

    repository.record(recordAt(yesterday, "yesterday-event"));
    repository.record(recordAt(today, "today-event"));

    List<UserActivityRecord> recent = repository.findRecentActivity(Integer.valueOf(1), 10);

    assertEquals(2, recent.size());
    assertEquals("today-event", recent.get(0).details().get("label"));
    assertEquals("yesterday-event", recent.get(1).details().get("label"));
  }

  @Test
  void findRecentActivity_honorsLimit(@TempDir Path basePath) {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    Instant base = Instant.now();
    for (int i = 0; i < 5; i++) {
      repository.record(recordAt(base.plusSeconds(i), "event-" + i));
    }

    List<UserActivityRecord> recent = repository.findRecentActivity(Integer.valueOf(1), 2);

    assertEquals(2, recent.size());
    assertEquals("event-4", recent.get(0).details().get("label"));
    assertEquals("event-3", recent.get(1).details().get("label"));
  }

  @Test
  void findRecentActivity_returnsEmptyList_whenLocationHasNoActivityYet(@TempDir Path basePath) {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    assertEquals(List.of(), repository.findRecentActivity(Integer.valueOf(99), 10));
  }

  @Test
  void purgeOlderThan_deletesDayFilesEntirelyBeforeCutoff_keepsNewerOnes(@TempDir Path basePath) {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    Instant now = Instant.now();
    Instant fortyDaysAgo = now.minus(40, java.time.temporal.ChronoUnit.DAYS);
    Instant twentyDaysAgo = now.minus(20, java.time.temporal.ChronoUnit.DAYS);
    Instant cutoff = now.minus(30, java.time.temporal.ChronoUnit.DAYS);

    repository.record(recordAt(fortyDaysAgo, "too-old"));
    repository.record(recordAt(twentyDaysAgo, "still-fresh"));
    repository.record(recordAt(now, "today"));

    Path locationDir = basePath.resolve("activity").resolve("location-1");
    Path oldFile = locationDir.resolve(DAY_FORMATTER.format(fortyDaysAgo) + ".jsonl");
    Path freshFile = locationDir.resolve(DAY_FORMATTER.format(twentyDaysAgo) + ".jsonl");
    Path todayFile = locationDir.resolve(DAY_FORMATTER.format(now) + ".jsonl");
    assertTrue(Files.exists(oldFile));
    assertTrue(Files.exists(freshFile));
    assertTrue(Files.exists(todayFile));

    repository.purgeOlderThan(cutoff);

    assertTrue(!Files.exists(oldFile), "Expected the 40-day-old activity file to be purged");
    assertTrue(Files.exists(freshFile), "Expected the 20-day-old activity file to survive");
    assertTrue(Files.exists(todayFile), "Expected today's activity file to survive");
  }

  @Test
  void purgeOlderThan_sweepsEveryLocation(@TempDir Path basePath) {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    Instant fortyDaysAgo = Instant.now().minus(40, java.time.temporal.ChronoUnit.DAYS);
    Instant cutoff = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);

    repository.record(new UserActivityRecord(Integer.valueOf(1), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, fortyDaysAgo, Map.of()));
    repository.record(new UserActivityRecord(Integer.valueOf(2), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, fortyDaysAgo, Map.of()));

    repository.purgeOlderThan(cutoff);

    String dayFileName = DAY_FORMATTER.format(fortyDaysAgo) + ".jsonl";
    assertTrue(!Files
        .exists(basePath.resolve("activity").resolve("location-1").resolve(dayFileName)));
    assertTrue(!Files
        .exists(basePath.resolve("activity").resolve("location-2").resolve(dayFileName)));
  }

  @Test
  void purgeOlderThan_doesNothing_whenNoActivityHasEverBeenRecorded(@TempDir Path basePath) {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    // Must not throw even though the "activity" directory doesn't exist yet.
    repository.purgeOlderThan(Instant.now());
  }

  // ── changeLocationId ─────────────────────────────────────────────────────

  @Test
  void changeLocationId_movesDayFilesToNewDirectory_andRewritesEachLinesLocationId(
      @TempDir Path basePath) throws Exception {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    Instant today = Instant.now();
    Instant yesterday = today.minusSeconds(86_400);
    repository.record(recordAt(yesterday, "first"));
    repository.record(recordAt(today, "second"));
    repository.record(recordAt(today, "third"));

    repository.changeLocationId(Integer.valueOf(1), Integer.valueOf(42));

    Path activityDir = basePath.resolve("activity");
    assertFalse(Files.exists(activityDir.resolve("location-1")),
        "The old location directory should be removed once emptied");

    List<UserActivityRecord> moved =
        repository.findRecentActivity(Integer.valueOf(42), 10);
    assertEquals(3, moved.size());
    assertEquals("third", moved.get(0).details().get("label"));
    assertEquals("second", moved.get(1).details().get("label"));
    assertEquals("first", moved.get(2).details().get("label"));
    for (UserActivityRecord record : moved) {
      assertEquals(Integer.valueOf(42), record.locationId());
    }
    assertTrue(repository.findRecentActivity(Integer.valueOf(1), 10).isEmpty());
  }

  @Test
  void changeLocationId_appendsToAnExistingDayFileUnderTheNewId_insteadOfOverwritingIt(
      @TempDir Path basePath) throws Exception {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    Instant occurredAt = Instant.now();
    repository.record(recordAt(occurredAt, "old"));
    repository.record(new UserActivityRecord(Integer.valueOf(42), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, occurredAt, Map.of("label", "new")));

    repository.changeLocationId(Integer.valueOf(1), Integer.valueOf(42));

    Path dayFile = basePath.resolve("activity").resolve("location-42")
        .resolve(DAY_FORMATTER.format(occurredAt) + ".jsonl");
    assertEquals(2, Files.readAllLines(dayFile).size());
  }

  @Test
  void changeLocationId_isANoOp_whenNoActivityExistsUnderTheOldId(@TempDir Path basePath) {

    UserActivityRepositoryFileSystemImpl repository =
        new UserActivityRepositoryFileSystemImpl(basePath.toString());

    repository.changeLocationId(Integer.valueOf(1), Integer.valueOf(42));

    assertFalse(Files.exists(basePath.resolve("activity").resolve("location-42")));
  }

  private static UserActivityRecord recordAt(Instant occurredAt, String label) {
    return new UserActivityRecord(Integer.valueOf(1), UserActivitySource.SWING_UI, "LOCAL",
        UserActivityType.TAB_NAVIGATION, occurredAt, Map.of("label", label));
  }
}
