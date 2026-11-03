package com.djt.jukeanator_engine.domain.songqueue.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

/** Tests for {@link SongQueueService#isPriorityPlayAllowed}. */
class SongQueueServicePriorityPlayRuleTest {

  private static final String ME = "me@example.com";
  private static final Predicate<SongQueueEntryDto> MINE = entry -> ME.equals(entry.username());

  private static SongQueueEntryDto entry(String username, int songId, int priority) {
    SongDto song =
        new SongDto(1, "Genre", 2, "Artist", 3, "Album", "/cover.jpg", songId, "Song", 1, 0);
    return new SongQueueEntryDto(username, song, priority, "/music/song" + songId + ".mp3");
  }

  @Test
  void notAllowed_whenTheQueueIsEmpty() {
    assertFalse(SongQueueService.isPriorityPlayAllowed(List.of(), MINE));
    assertFalse(SongQueueService.isPriorityPlayAllowed(null, MINE));
  }

  @Test
  void notAllowed_whenOnlyBackgroundMusicIsQueued() {
    assertFalse(SongQueueService.isPriorityPlayAllowed(
        List.of(entry("LOCAL", 1, 0), entry("LOCAL", 2, 0)), MINE));
  }

  @Test
  void notAllowed_whenOnlyTheCurrentUsersOwnSongsAreAboveBackgroundMusic() {
    assertFalse(SongQueueService.isPriorityPlayAllowed(
        List.of(entry(ME, 1, 2), entry(ME, 2, 1), entry("LOCAL", 3, 0)), MINE));
  }

  @Test
  void allowed_whenAnotherUsersNormalPlayIsQueued() {
    assertTrue(SongQueueService.isPriorityPlayAllowed(
        List.of(entry(ME, 1, 1), entry("other@example.com", 2, 1), entry("LOCAL", 3, 0)), MINE));
  }

  @Test
  void allowed_whileTheCurrentUserIsAheadOfAnotherUsersSong() {
    // The current user's earlier priority play is on top; another user's song is still waiting.
    assertTrue(SongQueueService.isPriorityPlayAllowed(
        List.of(entry(ME, 1, 2), entry("LOCAL", 2, 1)), MINE));
  }
}
